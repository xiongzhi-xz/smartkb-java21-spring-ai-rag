package com.smartkb.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL 持久化会话记忆。
 *
 * <p>会话与消息是需要审计和服务重启后恢复的业务事实，因此写入 PostgreSQL。Redis 只在后续
 * 作为可失效的摘要和热点上下文缓存使用。</p>
 *
 * <p>写入前原子递增 {@code conversation.next_sequence}，避免多实例并发写入同一会话时出现
 * 重复序号或不稳定顺序。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class PostgresChatMemory implements ChatMemory {

    private static final String ADVANCE_SEQUENCE_SQL = """
            INSERT INTO conversation (id, status, next_sequence, created_at, updated_at, last_message_at)
            VALUES (?, 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET status = 'ACTIVE',
                next_sequence = conversation.next_sequence + 1,
                updated_at = CURRENT_TIMESTAMP,
                last_message_at = CURRENT_TIMESTAMP
            RETURNING next_sequence
            """;

    private static final String INSERT_MESSAGE_SQL = """
            INSERT INTO conversation_message
                (id, conversation_id, sequence_no, message_type, content, created_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private static final String LOAD_RECENT_MESSAGES_SQL = """
            SELECT message_type, content
            FROM (
                SELECT message_type, content, sequence_no
                FROM conversation_message
                WHERE conversation_id = ?
                ORDER BY sequence_no DESC
                LIMIT ?
            ) recent_messages
            ORDER BY sequence_no ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }

        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            Long sequence = jdbcTemplate.queryForObject(ADVANCE_SEQUENCE_SQL, Long.class, conversationId);
            if (sequence == null) {
                throw new IllegalStateException("会话序号生成失败: " + conversationId);
            }
            jdbcTemplate.update(
                    INSERT_MESSAGE_SQL,
                    UUID.randomUUID(),
                    conversationId,
                    sequence,
                    messageType(message),
                    message.getContent()
            );
        }
        log.debug("已持久化会话消息: conversationId={}, count={}", conversationId, messages.size());
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (conversationId == null || conversationId.isBlank() || lastN <= 0) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                LOAD_RECENT_MESSAGES_SQL,
                conversationId,
                lastN
        );
        return rows.stream().map(this::toMessage).toList();
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM conversation_message WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("""
                UPDATE conversation
                SET status = 'CLEARED', updated_at = CURRENT_TIMESTAMP, last_message_at = NULL
                WHERE id = ?
                """, conversationId);
        log.info("已清除持久化会话消息: conversationId={}", conversationId);
    }

    private Message toMessage(Map<String, Object> row) {
        String type = String.valueOf(row.get("message_type"));
        String content = String.valueOf(row.getOrDefault("content", ""));
        return switch (type) {
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    private String messageType(Message message) {
        if (message instanceof AssistantMessage) {
            return "ASSISTANT";
        }
        if (message instanceof SystemMessage) {
            return "SYSTEM";
        }
        return "USER";
    }
}
