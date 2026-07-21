package com.smartkb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartKB 主启动类
 * <p>
 * 企业智能知识库系统 - 基于 Java 21 + Spring AI 的渐进式 RAG 架构
 * <p>
 * 核心特性：
 * - Java 21 Virtual Threads（全局启用）
 * - Spring AI Advisor 体系（RAG 核心）
 * - PostgreSQL 持久化会话、文档元数据与审计事实
 * - Redis 缓存、限流与分布式协调
 * - OpenTelemetry + Prometheus 可观测性
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@SpringBootApplication
public class SmartKbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartKbApplication.class, args);
    }
}
