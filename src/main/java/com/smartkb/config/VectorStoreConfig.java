package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * VectorStore 配置类
 * <p>
 * 核心功能：
 * 1. 配置 PostgreSQL + pgvector 作为向量存储
 * 2. 集成 Spring AI 的 PgVectorStore 抽象层
 * 3. 自动初始化向量表和 HNSW 索引
 * <p>
 * 技术选型理由：
 * - PostgreSQL + pgvector：2026 年个人/中小规模 RAG 项目主流选择
 * - 优势：关系数据 + 向量数据统一管理，降低运维成本
 * - HNSW 索引：高性能向量检索（优于 IVFFlat）
 * <p>
 * Spring AI PgVectorStore 特性：
 * - 统一的 VectorStore 接口（可无缝切换到 Pinecone、Milvus 等）
 * - 自动管理 Embedding 存储、检索、相似度计算
 * - 支持 Metadata 过滤（后续 Advanced RAG 会用到）
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public VectorStoreConfig(JdbcTemplate jdbcTemplate, @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 配置 PgVectorStore Bean
     * <p>
     * Spring AI 会自动：
     * 1. 创建 vector_store 表（id, content, metadata, embedding）
     * 2. 创建 HNSW 索引（基于 application.yml 配置）
     * 3. 提供 add/search/delete 等方法
     */
    @Bean
    public PgVectorStore vectorStore() {
        log.info("初始化 PgVectorStore...");

        PgVectorStore vectorStore = new PgVectorStore(jdbcTemplate, embeddingModel);

        log.info("PgVectorStore 初始化完成");
        return vectorStore;
    }
}
