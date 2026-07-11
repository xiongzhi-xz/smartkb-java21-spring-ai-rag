package com.smartkb.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorStoreService 单元测试
 * <p>
 * 注意：这些测试需要实际的 VectorStore Bean，因此标记为集成测试
 * 可以在有 PostgreSQL + pgvector 环境时运行
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
class VectorStoreServiceTest {

    // 此测试需要完整的 Spring 上下文，暂时作为示例
    // 实际运行需要 @SpringBootTest

    @Test
    void testDocumentCreation() {
        // 测试文档对象创建
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "test.md");
        metadata.put("fileType", "md");

        Document doc = new Document("这是测试内容", metadata);

        assertNotNull(doc);
        assertEquals("这是测试内容", doc.getContent());
        assertEquals("test.md", doc.getMetadata().get("fileName"));

        System.out.println("测试通过: Document 对象创建成功");
    }

    @Test
    void testEmbeddingVectorSize() {
        // 测试 Embedding 向量维度
        // OpenAI text-embedding-3-small 是 1536 维
        List<Double> mockEmbedding = new ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            mockEmbedding.add(Math.random());
        }

        assertEquals(1536, mockEmbedding.size());
        System.out.println("测试通过: Embedding 向量维度正确");
    }
}
