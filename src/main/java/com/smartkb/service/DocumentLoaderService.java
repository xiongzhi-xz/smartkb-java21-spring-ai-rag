package com.smartkb.service;

import com.smartkb.util.VirtualThreadInspector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 文档加载与智能切片服务
 * <p>
 * 核心功能：
 * 1. 支持多格式文档解析（PDF、Word、Markdown、TXT）
 * 2. 智能文本切片（基于 Token 分割，避免语义割裂）
 * 3. 使用 Java 21 Structured Concurrency 实现批量文档并发解析
 * <p>
 * 技术选型：
 * - Spring AI PagePdfDocumentReader：专门优化的 PDF 解析器
 * - Spring AI TikaDocumentReader：通用文档解析（Word、Markdown等）
 * - TokenTextSplitter：基于 Token 的智能分片（保证 chunk 不超过模型上下文窗口）
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class DocumentLoaderService {

    private static final int DEFAULT_CHUNK_SIZE = 800;      // Token数量（约600-800个中文字符）
    private static final int DEFAULT_CHUNK_OVERLAP = 100;   // 重叠部分，避免语义截断

    private final TokenTextSplitter textSplitter;

    public DocumentLoaderService() {
        this.textSplitter = new TokenTextSplitter();
    }

    /**
     * 加载并切片单个文档
     *
     * @param resource 文档资源（Spring Resource抽象，可来自文件、URL、classpath等）
     * @param fileType 文件类型（pdf、docx、md、txt）
     * @return 切片后的文档列表（每个Document包含一个chunk + metadata）
     */
    public List<Document> loadAndSplitDocument(Resource resource, String fileType) {
        log.info("开始加载文档: {}, 类型: {}", resource.getFilename(), fileType);
        VirtualThreadInspector.logThreadInfo("文档加载开始", "文件: " + resource.getFilename());

        try {
            // 1. 根据文件类型选择合适的 DocumentReader
            List<Document> documents = switch (fileType.toLowerCase()) {
                case "pdf" -> loadPdfDocument(resource);
                case "md", "txt" -> loadTextDocument(resource);
                case "docx", "doc" -> loadGenericDocument(resource);
                default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            };

            VirtualThreadInspector.logThreadInfo("文档解析完成", "原始文档数: " + documents.size());

            // 2. 智能切片（TokenTextSplitter 会保留 metadata）
            List<Document> chunks = textSplitter.apply(documents);

            log.info("文档切片完成: {} -> {} chunks", resource.getFilename(), chunks.size());
            VirtualThreadInspector.logThreadInfo("文档切片完成", "chunks: " + chunks.size());
            return chunks;

        } catch (IllegalArgumentException e) {
            log.error("文档加载失败: {}", resource.getFilename(), e);
            throw e;
        } catch (Exception e) {
            log.error("文档加载失败: {}", resource.getFilename(), e);
            throw new RuntimeException("文档加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量加载并切片多个文档（使用 Virtual Threads 并发处理）
     * <p>
     * 使用 Java 21 Virtual Thread Executor：
     * - newVirtualThreadPerTaskExecutor 为每个任务创建虚拟线程
     * - IO 阻塞时自动释放平台线程，支持高并发
     * <p>
     * Virtual Threads 优势：
     * - 传统线程池：最多20-50个线程，IO阻塞时浪费
     * - Virtual Threads：可并发处理50+文档，IO阻塞时自动释放平台线程
     *
     * @param resources 文档资源列表
     * @param fileType  文件类型
     * @return 所有文档的切片列表
     */
    public List<Document> loadAndSplitDocumentsBatch(List<Resource> resources, String fileType) {
        log.info("批量加载文档: {} 个文件, 类型: {}", resources.size(), fileType);
        VirtualThreadInspector.logThreadInfo("批量文档加载开始", "文件数: " + resources.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            log.info("使用 Virtual Thread Executor 并发处理 {} 个文档", resources.size());

            // 为每个文档启动一个虚拟线程进行解析
            var futures = resources.stream()
                    .map(resource -> executor.submit(() -> {
                        VirtualThreadInspector.logThreadInfo("文档解析任务", "文件: " + resource.getFilename());
                        return loadAndSplitDocument(resource, fileType);
                    }))
                    .toList();

            // 等待所有文档解析完成，合并结果
            List<Document> allChunks = futures.stream()
                    .flatMap(future -> {
                        try {
                            return future.get().stream();
                        } catch (Exception e) {
                            log.error("文档加载任务失败", e);
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());

            log.info("批量加载完成: 总计 {} chunks", allChunks.size());
            VirtualThreadInspector.logThreadInfo("批量文档加载完成", "总chunks: " + allChunks.size());
            return allChunks;

        } catch (Exception e) {
            log.error("批量文档加载失败", e);
            throw new RuntimeException("批量文档加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载 PDF 文档（使用 Spring AI 优化的 PDF Reader）
     */
    private List<Document> loadPdfDocument(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfBottomTextLinesToDelete(3)  // 删除页脚（页码等）
                        .withNumberOfTopPagesToSkipBeforeDelete(1)  // 跳过封面
                        .build())
                .withPagesPerDocument(1)  // 每页作为一个 Document（后续再切片）
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
        return pdfReader.get();
    }

    /**
     * 加载纯文本文件（Markdown/TXT），固定按 UTF-8 读取，避免 Tika 自动探测导致中文乱码
     */
    private List<Document> loadTextDocument(Resource resource) throws Exception {
        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return List.of(new Document(content));
        }
    }

    /**
     * 加载通用文档（Word 等，使用 Apache Tika）
     */
    private List<Document> loadGenericDocument(Resource resource) {
        TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
        return tikaReader.get();
    }
}
