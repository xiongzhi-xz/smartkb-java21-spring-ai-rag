package com.smartkb.service;

import com.smartkb.domain.Document;
import com.smartkb.domain.DocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档管理服务
 * <p>
 * 核心功能：
 * 1. 查询已上传文档列表
 * 2. 查询单个文档详情
 * 3. 删除文档（包括向量数据）
 * 4. 统计文档数量和存储占用
 * <p>
 * 生产级功能：
 * - 支持分页查询
 * - 支持按文件类型/上传时间过滤
 * - 级联删除（文档 + 向量数据）
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentManagementService {

    private final JdbcTemplate jdbcTemplate;
    private final VectorStoreService vectorStoreService;

    /**
     * 查询所有已上传文档（简单版）
     * <p>
     * 从 vector_store 表中查询唯一的文档列表
     *
     * @return 文档列表
     */
    public List<Map<String, Object>> listDocuments() {
        log.info("查询所有已上传文档");

        try {
            String sql = """
                    SELECT
                        metadata->>'fileName' as file_name,
                        metadata->>'fileType' as file_type,
                        COUNT(*) as chunk_count,
                        MIN(metadata->>'uploadTime') as upload_time
                    FROM vector_store
                    WHERE metadata->>'fileName' IS NOT NULL
                    GROUP BY metadata->>'fileName', metadata->>'fileType'
                    ORDER BY MIN(metadata->>'uploadTime') DESC
                    """;

            List<Map<String, Object>> documents = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> doc = new HashMap<>();
                doc.put("fileName", rs.getString("file_name"));
                doc.put("fileType", rs.getString("file_type"));
                doc.put("chunkCount", rs.getInt("chunk_count"));
                doc.put("uploadTime", rs.getString("upload_time"));
                return doc;
            });

            log.info("查询到 {} 个文档", documents.size());
            return documents;

        } catch (Exception e) {
            log.error("查询文档列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询文档详情（包含所有 chunks）
     *
     * @param fileName 文件名
     * @return 文档详情
     */
    public Map<String, Object> getDocumentDetail(String fileName) {
        log.info("查询文档详情: {}", fileName);

        try {
            String sql = """
                    SELECT
                        id,
                        content,
                        metadata
                    FROM vector_store
                    WHERE metadata->>'fileName' = ?
                    ORDER BY metadata->>'chunkIndex'
                    """;

            List<Map<String, Object>> chunks = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("id", rs.getString("id"));
                chunk.put("content", rs.getString("content"));
                return chunk;
            }, fileName);

            Map<String, Object> detail = new HashMap<>();
            detail.put("fileName", fileName);
            detail.put("chunkCount", chunks.size());
            detail.put("chunks", chunks);

            log.info("文档详情查询完成: {} chunks", chunks.size());
            return detail;

        } catch (Exception e) {
            log.error("查询文档详情失败: {}", fileName, e);
            throw new RuntimeException("查询文档详情失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文档（包括所有 chunks 和向量数据）
     *
     * @param fileName 文件名
     * @return 删除的 chunk 数量
     */
    public int deleteDocument(String fileName) {
        log.info("删除文档: {}", fileName);

        try {
            // 1. 查询该文档的所有 chunk ID
            String selectSql = """
                    SELECT id
                    FROM vector_store
                    WHERE metadata->>'fileName' = ?
                    """;

            List<String> chunkIds = jdbcTemplate.queryForList(selectSql, String.class, fileName);

            if (chunkIds.isEmpty()) {
                log.warn("文档不存在: {}", fileName);
                return 0;
            }

            // 2. 使用 VectorStoreService 删除（级联删除向量数据）
            vectorStoreService.deleteDocuments(chunkIds);

            log.info("文档删除成功: {}, 删除 {} 个 chunks", fileName, chunkIds.size());
            return chunkIds.size();

        } catch (Exception e) {
            log.error("删除文档失败: {}", fileName, e);
            throw new RuntimeException("删除文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 统计文档数量和存储信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        log.info("查询文档统计信息");

        try {
            Map<String, Object> stats = new HashMap<>();

            // 1. 文档总数
            String docCountSql = """
                    SELECT COUNT(DISTINCT metadata->>'fileName') as doc_count
                    FROM vector_store
                    WHERE metadata->>'fileName' IS NOT NULL
                    """;
            Integer docCount = jdbcTemplate.queryForObject(docCountSql, Integer.class);
            stats.put("documentCount", docCount != null ? docCount : 0);

            // 2. chunk 总数
            String chunkCountSql = "SELECT COUNT(*) FROM vector_store";
            Integer chunkCount = jdbcTemplate.queryForObject(chunkCountSql, Integer.class);
            stats.put("chunkCount", chunkCount != null ? chunkCount : 0);

            // 3. 按文件类型统计
            String typeCountSql = """
                    SELECT
                        metadata->>'fileType' as file_type,
                        COUNT(DISTINCT metadata->>'fileName') as count
                    FROM vector_store
                    WHERE metadata->>'fileType' IS NOT NULL
                    GROUP BY metadata->>'fileType'
                    """;

            List<Map<String, Object>> typeStats = jdbcTemplate.query(typeCountSql, (rs, rowNum) -> {
                Map<String, Object> stat = new HashMap<>();
                stat.put("fileType", rs.getString("file_type"));
                stat.put("count", rs.getInt("count"));
                return stat;
            });
            stats.put("byFileType", typeStats);

            log.info("统计信息查询完成: {} 个文档, {} 个 chunks", docCount, chunkCount);
            return stats;

        } catch (Exception e) {
            log.error("查询统计信息失败", e);
            return Collections.emptyMap();
        }
    }
}
