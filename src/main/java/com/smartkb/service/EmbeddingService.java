package com.smartkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Embedding 生成服务
 * <p>
 * 核心功能：
 * 1. 调用 OpenAI Embedding API 生成文本向量
 * 2. 使用 Virtual Threads 实现批量文档的并发 Embedding 处理
 * 3. 支持批次控制（避免 API 限流）
 * <p>
 * 技术选型：
 * - Spring AI EmbeddingModel：统一抽象层，可切换不同 Embedding 提供商
 * - text-embedding-3-small：OpenAI 最新模型（性价比高，1536 维度）
 * <p>
 * Virtual Threads 应用场景：
 * - 批量文档 Embedding：IO 密集型（HTTP API 调用），虚拟线程可大幅提升吞吐量
 * - 传统线程池：20-50 个线程，API 响应时间 500ms，QPS 最多 40-100
 * - Virtual Threads：并发 30+ 请求，充分利用 API 配额，QPS 可达 60+
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    private static final int BATCH_SIZE = 10;  // 每批次处理的文档数量（避免单个请求过大）

    /**
     * 为单个文本生成 Embedding 向量
     *
     * @param text 输入文本
     * @return Embedding 向量（float[]）
     */
    public List<Double> embedText(String text) {
        log.debug("生成单个文本的 Embedding, 长度: {} 字符", text.length());

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            return response.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.error("Embedding 生成失败: {}", text.substring(0, Math.min(50, text.length())), e);
            throw new RuntimeException("Embedding 生成失败", e);
        }
    }

    /**
     * 批量生成 Embedding（使用 Virtual Threads 并发处理）
     * <p>
     * 实现策略：
     * 1. 将文档列表按 BATCH_SIZE 分批
     * 2. 每批启动一个虚拟线程并发调用 API
     * 3. 使用 Virtual Thread Executor 统一管理所有批次任务
     * <p>
     * 性能对比：
     * - 串行处理 100 个文档（每个 500ms）：50 秒
     * - Virtual Threads 并发 10 批（每批 10 个）：约 5 秒
     *
     * @param documents 文档列表（Document 包含 content + metadata）
     * @return 文档列表（已填充 embedding 字段）
     */
    public List<Document> embedDocumentsBatch(List<Document> documents) {
        if (documents.isEmpty()) {
            return documents;
        }

        log.info("开始批量生成 Embedding: {} 个文档", documents.size());
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 分批处理（每批 BATCH_SIZE 个文档）
            List<List<Document>> batches = partitionList(documents, BATCH_SIZE);

            // 为每批启动一个虚拟线程
            var futures = batches.stream()
                    .map(batch -> executor.submit(() -> {
                        embedBatch(batch);
                        return null;
                    }))
                    .toList();

            // 等待所有批次完成
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception e) {
                    log.error("批次 Embedding 任务失败", e);
                    throw new RuntimeException("批次 Embedding 任务失败", e);
                }
            });

            long duration = System.currentTimeMillis() - startTime;
            log.info("批量 Embedding 完成: {} 个文档, 耗时: {} ms, 平均: {} ms/doc",
                    documents.size(), duration, duration / documents.size());

            return documents;

        } catch (Exception e) {
            log.error("批量 Embedding 生成失败", e);
            throw new RuntimeException("批量 Embedding 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理单个批次的 Embedding（在虚拟线程中执行）
     */
    private void embedBatch(List<Document> batch) {
        try {
            // 提取文本内容
            List<String> texts = batch.stream()
                    .map(Document::getContent)
                    .toList();

            // 调用 Embedding API（IO 阻塞操作，虚拟线程会自动 park）
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);

            // 将 Embedding 向量填充回 Document（Spring AI 会自动处理）
            for (int i = 0; i < batch.size(); i++) {
                List<Double> embedding = response.getResults().get(i).getOutput();
                batch.get(i).setEmbedding(embedding);
            }

            log.debug("批次 Embedding 完成: {} 个文档", batch.size());

        } catch (Exception e) {
            log.error("批次 Embedding 失败: {} 个文档", batch.size(), e);
            throw new RuntimeException("批次 Embedding 失败", e);
        }
    }

    /**
     * 将列表分批（工具方法）
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }
}
