package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * <p>
 * 核心职责：
 * 1. 配置 RedisTemplate（String Key + String Value）
 * 2. 用于 ChatMemory 的会话消息存储
 * <p>
 * 设计选择：使用 StringRedisSerializer 而非 JDK 序列化
 * - 可读性好：Redis 中存储的是 JSON 文本，可直接通过 redis-cli 查看
 * - 无类依赖：不绑定 Java 类结构，版本升级不 break
 * - 跨语言：如果后续有非 Java 服务读取会话，也能解析
 * <p>
 * 面试讲法要点：
 * - 为什么用 String 序列化而不用 JDK 序列化？
 *   JDK 序列化有安全风险（反序列化漏洞）、不可读、类变更就 break。
 *   String + JSON 是 Redis 的最佳实践，特别是 ChatMemory 场景消息格式简单。
 * - RedisTemplate 和 StringRedisTemplate 的选择：
 *   StringRedisTemplate 已经预设了 String 序列化，但显式配置更清晰、
 *   且允许我们统一 Key 前缀、自定义连接工厂参数。
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate（String Key / String Value）
     * <p>
     * 使用场景：
     * - ChatMemory 会话消息存储（smartkb:chat:{conversationId}）
     * - 后续缓存场景复用
     *
     * @param connectionFactory Redis 连接工厂（由 Spring Boot 自动配置）
     * @return RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("初始化 RedisTemplate (String Key/Value)");

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 和 Value 均使用 String 序列化
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
