package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的 ChatMemory 实现
 * <p>
 * 核心设计：
 * 1. 使用 Redis List 结构存储会话消息（按 conversationId 隔离）
 * 2. 每条消息序列化为 JSON 存储，包含 messageType + content
 * 3. 支持 TTL 过期策略，自动清理长期不活跃的会话
 * <p>
 * 相比 InMemoryChatMemory 的优势：
 * - 服务重启后会话不丢失
 * - 支持分布式会话共享（多个实例可共享同一 Redis）
 * - 天然支持会话过期（TTL），避免内存无限增长
 * - 可与 Redis 缓存、限流共享基础设施
 * <p>
 * Redis 存储结构：
 * - Key 格式：smartkb:chat:{conversationId}
 * - Value：List<String>，每个元素是消息的 JSON 序列化
 * - TTL：默认 24 小时，可配置
 * <p>
 * 面试讲法要点：
 * - 为什么不用 Spring AI 官方 RedisChatMemory？1.0.0-M1 版本没有提供，
 *   自研实现 ChatMemory 接口只需 add/get/clear 三个方法，非常轻量。
 * - Redis List + TTL 的设计权衡：
 *   List 天然有序、支持 LPUSH/LRANGE 高效操作；
 *   TTL 自动过期避免僵尸会话占内存，24h 是业务合理的会话窗口。
 * - 消息序列化用简单 JSON 而非 Java 序列化：
 *   避免类变更导致反序列化失败，JSON 可读性好、可跨语言。
 * - 为什么 add 时续期 TTL？
 *   活跃会话持续续期，确保用户在某会话持续对话时不会被过期清理。
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
public class RedisChatMemory implements ChatMemory {

    /** Redis Key 前缀 */
    private static final String KEY_PREFIX = "smartkb:chat:";

    /** 默认会话过期时间（24 小时） */
    private static final long DEFAULT_TTL_HOURS = 24;

    /** 每个会话最大保留消息数，防止列表无限增长 */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 100;

    private final RedisTemplate<String, String> redisTemplate;
    private final long ttlHours;

    /**
     * 构造函数（使用默认 24 小时 TTL）
     *
     * @param redisTemplate Redis 操作模板
     */
    public RedisChatMemory(RedisTemplate<String, String> redisTemplate) {
        this(redisTemplate, DEFAULT_TTL_HOURS);
    }

    /**
     * 构造函数（自定义 TTL）
     *
     * @param redisTemplate Redis 操作模板
     * @param ttlHours      会话过期时间（小时）
     */
    public RedisChatMemory(RedisTemplate<String, String> redisTemplate, long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours;
        log.info("初始化 RedisChatMemory, TTL={}h", ttlHours);
    }

    /**
     * 添加单条消息到会话
     *
     * @param conversationId 会话 ID
     * @param message        消息
     */
    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    /**
     * 批量添加消息到会话
     * <p>
     * 实现逻辑：
     * 1. 将每条消息序列化为 JSON 字符串
     * 2. 使用 Redis RPUSH 追加到 List 末尾
     * 3. 裁剪超出上限的旧消息（LTRIM）
     * 4. 续期 TTL（活跃会话不过期）
     *
     * @param conversationId 会话 ID
     * @param messages       消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isEmpty()) {
            log.warn("conversationId 为空，跳过消息存储");
            return;
        }
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String key = buildKey(conversationId);

        try {
            // 批量序列化并追加到 Redis List
            List<String> serialized = messages.stream()
                    .map(this::serializeMessage)
                    .collect(Collectors.toList());

            redisTemplate.opsForList().rightPushAll(key, serialized);

            // 裁剪超限消息（保留最新的 MAX_MESSAGES_PER_CONVERSATION 条）
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > MAX_MESSAGES_PER_CONVERSATION) {
                long start = size - MAX_MESSAGES_PER_CONVERSATION;
                redisTemplate.opsForList().trim(key, start, -1);
            }

            // 活跃会话续期 TTL
            redisTemplate.expire(key, ttlHours, TimeUnit.HOURS);

            log.debug("会话 {} 添加 {} 条消息，当前共 {} 条",
                    conversationId, messages.size(),
                    redisTemplate.opsForList().size(key));

        } catch (Exception e) {
            // Redis 写入失败不应阻断对话流程，降级为日志告警
            log.error("Redis 写入会话消息失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 获取会话最近 N 条消息
     * <p>
     * 实现逻辑：
     * 1. Redis LRANGE 从 List 尾部取 lastN 条
     * 2. 反序列化为 Spring AI Message 对象
     * 3. 读取时同样续期 TTL
     *
     * @param conversationId 会话 ID
     * @param lastN          取最近 N 条
     * @return 消息列表（按时间正序）
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (conversationId == null || conversationId.isEmpty()) {
            return Collections.emptyList();
        }

        String key = buildKey(conversationId);

        try {
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return Collections.emptyList();
            }

            // 计算起止位置：从 List 尾部取 lastN 条
            long start = Math.max(0, size - lastN);
            List<String> rawMessages = redisTemplate.opsForList().range(key, start, -1);

            if (rawMessages == null || rawMessages.isEmpty()) {
                return Collections.emptyList();
            }

            // 读取时续期 TTL
            redisTemplate.expire(key, ttlHours, TimeUnit.HOURS);

            List<Message> messages = rawMessages.stream()
                    .map(this::deserializeMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("会话 {} 读取最近 {} 条消息", conversationId, messages.size());
            return messages;

        } catch (Exception e) {
            // Redis 读取失败时返回空列表，不影响当前对话（只是丢失历史上下文）
            log.error("Redis 读取会话消息失败: conversationId={}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除指定会话的所有消息
     *
     * @param conversationId 会话 ID
     */
    @Override
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }

        String key = buildKey(conversationId);

        try {
            Boolean deleted = redisTemplate.delete(key);
            log.info("会话 {} 已清除, deleted={}", conversationId, deleted);
        } catch (Exception e) {
            log.error("Redis 清除会话失败: conversationId={}", conversationId, e);
        }
    }

    // ========== 序列化/反序列化 ==========

    /**
     * 将 Message 序列化为 JSON 字符串
     * <p>
     * 格式：{"type":"USER","content":"xxx"}
     * 不使用 Java 序列化，避免类变更导致反序列化失败
     */
    private String serializeMessage(Message message) {
        String messageType = getMessageType(message);
        // 手动拼接 JSON，避免引入额外依赖
        return String.format("{\"type\":\"%s\",\"content\":%s}",
                messageType, escapeJsonContent(message.getContent()));
    }

    /**
     * 将 JSON 字符串反序列化为 Message 对象
     */
    private Message deserializeMessage(String json) {
        try {
            // 简易解析：提取 type 和 content 字段
            String type = extractJsonValue(json, "type");
            String content = extractJsonStringValue(json, "content");

            if (type == null || content == null) {
                log.warn("消息反序列化失败，格式异常: {}", json);
                return null;
            }

            return switch (type) {
                case "USER" -> new UserMessage(content);
                case "ASSISTANT" -> new AssistantMessage(content);
                case "SYSTEM" -> new SystemMessage(content);
                default -> {
                    log.warn("未知消息类型: {}, 降级为 UserMessage", type);
                    yield new UserMessage(content);
                }
            };
        } catch (Exception e) {
            log.error("消息反序列化异常: {}", json, e);
            return null;
        }
    }

    /**
     * 获取消息类型标识
     */
    private String getMessageType(Message message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AssistantMessage) return "ASSISTANT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return "USER";
    }

    /**
     * 转义 JSON 字符串值中的特殊字符
     */
    private String escapeJsonContent(String content) {
        if (content == null) return "\"\"";
        return "\"" + content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    /**
     * 提取 JSON 中的字段值（简易实现，不依赖 JSON 库）
     */
    private String extractJsonValue(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                value.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
        }
        return value.toString();
    }

    /**
     * 提取 JSON 中带转义处理的字符串值
     */
    private String extractJsonStringValue(String json, String field) {
        String raw = extractJsonValue(json, field);
        if (raw == null) return null;
        // 处理 JSON 转义序列
        return raw
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
