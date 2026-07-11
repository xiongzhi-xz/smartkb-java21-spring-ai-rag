package com.smartkb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartKB 主启动类
 * <p>
 * 企业智能知识库系统 - 基于 Java 21 + Spring AI + pgvector
 * <p>
 * 核心特性：
 * - Java 21 Virtual Threads（全局启用）
 * - Spring AI Advisor 体系（RAG 核心）
 * - PostgreSQL + pgvector 向量存储
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
