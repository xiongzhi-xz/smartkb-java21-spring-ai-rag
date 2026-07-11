package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.PgVectorStore.PgIndexType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
 * Embedding 模型与维度对应关系（重要）：
 * - nomic-embed-text（Ollama 本地）：768 维 ← 当前使用
 * - text-embedding-3-small（OpenAI）：1536 维
 * - 维度不匹配会导致向量检索完全失效
 * <p>
 * 如果切换 Embedding 模型：
 * 1. 修改 application.yml 中 ollama.embedding.options.model
 * 2. 同步修改 pgvector.dimensions
 * 3. 删除旧的 vector_store 表并重新上传文档
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    @Value("${spring.ai.vectorstore.pgvector.dimensions:768}")
    private int expectedDimensions;

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
     * <p>
     * 维度校验：
     * 启动时检测数据库中已有表的维度，与配置不匹配时打印警告
     */
    @Bean
    public PgVectorStore vectorStore() {
        log.info("初始化 PgVectorStore (期望维度: {})...", expectedDimensions);
        checkDimensionCompatibility();

        PgVectorStore vectorStore = new PgVectorStore(
                jdbcTemplate,
                embeddingModel,
                expectedDimensions,
                PgDistanceType.COSINE_DISTANCE,
                false,
                PgIndexType.HNSW,
                true
        );

        log.info("PgVectorStore 初始化完成");
        return vectorStore;
    }

    /**
     * 检查数据库中已有 vector_store 表的维度是否与配置匹配
     * <p>
     * 如果维度不匹配（比如之前用了 1536 维，现在改成 768 维），
     * 需要删除旧表重新建，否则向量检索会报错或结果异常。
     */
    private void checkDimensionCompatibility() {
        try {
            Integer actualDimensions = jdbcTemplate.queryForObject(
                    "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'",
                    Integer.class);

            if (actualDimensions != null) {
                // pgvector 维度信息编码方式：atttypmod = 实际维度
                // 对于 vector(N) 类型，atttypmod 是 -1（无法直接获取维度），用另一种方式
                String dimStr = jdbcTemplate.queryForObject(
                        "SELECT format_type(atttypid, atttypmod) FROM pg_attribute WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'",
                        String.class);

                if (dimStr != null && dimStr.contains("vector(")) {
                    String dimNumber = dimStr.replaceAll(".*vector\\((\\d+)\\).*", "$1");
                    int dbDimensions = Integer.parseInt(dimNumber);

                    if (dbDimensions != expectedDimensions) {
                        log.warn("========================================");
                        log.warn("⚠ pgvector 维度不匹配!");
                        log.warn("  数据库中 vector_store 表维度: {}", dbDimensions);
                        log.warn("  当前配置期望维度: {}", expectedDimensions);
                        log.warn("  请执行: DELETE FROM vector_store; 再重新上传文档");
                        log.warn("========================================");
                    } else {
                        log.info("pgvector 维度校验通过: {} 维", dbDimensions);
                    }
                } else {
                    log.info("vector_store 表不存在或首次创建，跳过维度校验");
                }
            }
        } catch (Exception e) {
            // 表不存在或其他情况，不影响启动
            log.info("vector_store 表不存在或首次创建，跳过维度校验");
        }
    }
}
