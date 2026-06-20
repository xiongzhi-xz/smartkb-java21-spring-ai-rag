package com.smartkb.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentLoaderService 单元测试
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
class DocumentLoaderServiceTest {

    private final DocumentLoaderService documentLoaderService = new DocumentLoaderService();

    @Test
    void testLoadAndSplitDocument_Markdown() {
        // 准备测试数据
        Resource resource = new ClassPathResource("test-docs/virtual-threads-guide.md");

        // 执行测试
        List<Document> chunks = documentLoaderService.loadAndSplitDocument(resource, "md");

        // 验证结果
        assertNotNull(chunks);
        assertTrue(chunks.size() > 0, "应该至少有一个文档块");

        // 验证第一个 chunk 包含内容
        Document firstChunk = chunks.get(0);
        assertNotNull(firstChunk.getContent());
        assertTrue(firstChunk.getContent().length() > 0);

        String allContent = chunks.stream()
                .map(Document::getContent)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(allContent.contains("虚拟线程"), "Markdown 中文内容应该按 UTF-8 正确读取");

        System.out.println("测试通过: 文档切片数量 = " + chunks.size());
    }

    @Test
    void testLoadAndSplitDocument_UnsupportedType() {
        // 准备测试数据
        Resource resource = new ClassPathResource("test-docs/virtual-threads-guide.md");

        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            documentLoaderService.loadAndSplitDocument(resource, "unsupported");
        });

        assertTrue(exception.getMessage().contains("不支持的文件类型"));
        System.out.println("测试通过: 正确抛出不支持文件类型异常");
    }

    @Test
    void testLoadAndSplitDocumentsBatch() {
        // 准备测试数据（多个文档）
        Resource resource = new ClassPathResource("test-docs/virtual-threads-guide.md");
        List<Resource> resources = List.of(resource, resource);  // 模拟2个相同文档

        // 执行测试
        List<Document> chunks = documentLoaderService.loadAndSplitDocumentsBatch(resources, "md");

        // 验证结果
        assertNotNull(chunks);
        assertTrue(chunks.size() > 0, "批量加载应该返回文档块");

        System.out.println("测试通过: 批量加载文档块数量 = " + chunks.size());
    }

    @Test
    void testLoadAndSplitDocument_LongTextUsesEmbeddingSafeChunks() {
        String content = "标题\n\n" + "这是一个很长的 PDF 提取段落，没有足够自然断点，容易超过本地 Embedding 模型上下文。".repeat(260);
        Resource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "long-text.md";
            }
        };

        List<Document> chunks = documentLoaderService.loadAndSplitDocument(resource, "md");

        assertTrue(chunks.size() > 1, "长文本应该被切成多个安全片段");
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getContent().length() <= 1500),
                "每个片段都应该低于 Ollama Embedding 安全字符长度");
    }
}
