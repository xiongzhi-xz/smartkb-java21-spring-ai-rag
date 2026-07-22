package com.smartkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
     * 查询所有已上传文档。
     * <p>
     * 企业文档表是生命周期事实来源；vector_store 只用于补充已完成文档的
     * chunk 数量，并保留旧链路文档的兼容展示。
     *
     * @return 文档列表
     */
    public List<Map<String, Object>> listDocuments() {
        log.info("查询所有已上传文档");

        List<Map<String, Object>> enterpriseDocuments = listEnterpriseDocuments();
        Set<String> enterpriseFileNames = enterpriseDocuments.stream()
                .map(document -> String.valueOf(document.get("fileName")))
                .collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> legacyDocuments = listLegacyDocuments();
        legacyDocuments.removeIf(document -> enterpriseFileNames.contains(document.get("fileName")));
        enterpriseDocuments.addAll(legacyDocuments);
        log.info("查询到 {} 个文档（企业文档 {} 个，兼容文档 {} 个）",
                enterpriseDocuments.size(),
                enterpriseDocuments.size() - legacyDocuments.size(),
                legacyDocuments.size());
        return enterpriseDocuments;
    }

    /**
     * 查询文档详情（包含所有 chunks）
     *
     * @param fileName 文件名（兼容旧接口）
     * @return 文档详情
     */
    public Map<String, Object> getDocumentDetail(String fileName) {
        log.info("查询文档详情: {}", fileName);

        try {
            Optional<UUID> enterpriseDocumentId = jdbcTemplate.query("""
                    SELECT id
                    FROM kb_document
                    WHERE file_name = ?
                    ORDER BY updated_at DESC
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class), fileName)
                    .stream()
                    .findFirst();
            if (enterpriseDocumentId.isPresent()) {
                return getDocumentDetail(enterpriseDocumentId.get());
            }
        } catch (Exception e) {
            log.debug("企业文档名查询不可用，回退旧 vector_store: {}", fileName, e);
        }

        return getLegacyDocumentDetail(fileName);
    }

    /** 按企业文档 ID 查询生命周期、最新任务和 Chunk 状态。 */
    public Map<String, Object> getDocumentDetail(UUID documentId) {
        log.info("查询企业文档详情: {}", documentId);
        Map<String, Object> detail = jdbcTemplate.query("""
                SELECT d.id, d.knowledge_base_id, d.file_name, d.content_type, d.object_key,
                       d.content_checksum, d.size_bytes, d.version_no, d.status AS document_status,
                       d.created_at, d.updated_at,
                       j.id AS job_id, j.status AS job_status, j.retry_count,
                       j.error_code, j.error_message, j.started_at, j.finished_at
                FROM kb_document d
                LEFT JOIN LATERAL (
                    SELECT id, status, retry_count, error_code, error_message,
                           started_at, finished_at, created_at
                    FROM ingestion_job
                    WHERE document_id = d.id
                    ORDER BY created_at DESC
                    LIMIT 1
                ) j ON TRUE
                WHERE d.id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> document = new HashMap<>();
            document.put("documentId", rs.getObject("id", UUID.class).toString());
            document.put("knowledgeBaseId", rs.getObject("knowledge_base_id", UUID.class).toString());
            document.put("fileName", rs.getString("file_name"));
            document.put("fileType", fileType(rs.getString("file_name")));
            document.put("contentType", rs.getString("content_type"));
            document.put("objectKey", rs.getString("object_key"));
            document.put("contentChecksum", rs.getString("content_checksum"));
            document.put("sizeBytes", rs.getLong("size_bytes"));
            document.put("versionNo", rs.getInt("version_no"));
            document.put("status", rs.getString("document_status"));
            document.put("createdAt", String.valueOf(rs.getObject("created_at")));
            document.put("updatedAt", String.valueOf(rs.getObject("updated_at")));

            Map<String, Object> job = new HashMap<>();
            UUID jobId = rs.getObject("job_id", UUID.class);
            if (jobId != null) {
                job.put("jobId", jobId.toString());
                job.put("status", rs.getString("job_status"));
                job.put("retryCount", rs.getInt("retry_count"));
                job.put("errorCode", rs.getString("error_code"));
                job.put("errorMessage", rs.getString("error_message"));
                job.put("startedAt", nullableString(rs.getObject("started_at")));
                job.put("finishedAt", nullableString(rs.getObject("finished_at")));
            }
            document.put("job", job);

            List<Map<String, Object>> chunks = findChunks(documentId);
            document.put("chunkCount", chunks.size());
            document.put("chunks", chunks);
            return document;
        }, documentId).stream().findFirst().orElseThrow(() ->
                new IllegalArgumentException("文档不存在: " + documentId));

        log.info("企业文档详情查询完成: documentId={}, chunks={}", documentId, detail.get("chunkCount"));
        return detail;
    }

    private List<Map<String, Object>> listEnterpriseDocuments() {
        try {
            return jdbcTemplate.query("""
                    SELECT d.id, d.file_name, d.content_type, d.status AS document_status,
                           d.size_bytes, d.version_no, d.created_at, d.updated_at,
                           j.id AS job_id, j.status AS job_status, j.retry_count,
                           j.error_code, COUNT(v.id) AS chunk_count
                    FROM kb_document d
                    LEFT JOIN LATERAL (
                        SELECT id, status, retry_count, error_code, created_at
                        FROM ingestion_job
                        WHERE document_id = d.id
                        ORDER BY created_at DESC
                        LIMIT 1
                    ) j ON TRUE
                    LEFT JOIN vector_store v
                        ON v.metadata->>'documentId' = d.id::text
                    GROUP BY d.id, d.file_name, d.content_type, d.status,
                             d.size_bytes, d.version_no, d.created_at, d.updated_at,
                             j.id, j.status, j.retry_count, j.error_code
                    ORDER BY d.updated_at DESC
                    """, (rs, rowNum) -> {
                Map<String, Object> document = new HashMap<>();
                UUID documentId = rs.getObject("id", UUID.class);
                document.put("documentId", documentId.toString());
                document.put("fileName", rs.getString("file_name"));
                document.put("fileType", fileType(rs.getString("file_name")));
                document.put("contentType", rs.getString("content_type"));
                document.put("status", rs.getString("document_status"));
                document.put("chunkCount", rs.getInt("chunk_count"));
                document.put("sizeBytes", rs.getLong("size_bytes"));
                document.put("versionNo", rs.getInt("version_no"));
                document.put("createdAt", nullableString(rs.getObject("created_at")));
                document.put("updatedAt", nullableString(rs.getObject("updated_at")));
                UUID jobId = rs.getObject("job_id", UUID.class);
                if (jobId != null) {
                    document.put("jobId", jobId.toString());
                    document.put("jobStatus", rs.getString("job_status"));
                    document.put("retryCount", rs.getInt("retry_count"));
                    document.put("errorCode", rs.getString("error_code"));
                }
                return document;
            });
        } catch (Exception e) {
            log.warn("企业文档列表查询失败，将仅返回兼容 vector_store 文档", e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> listLegacyDocuments() {
        try {
            return jdbcTemplate.query("""
                    SELECT metadata->>'fileName' AS file_name,
                           metadata->>'fileType' AS file_type,
                           COUNT(*) AS chunk_count,
                           MIN(metadata->>'uploadTime') AS upload_time
                    FROM vector_store
                    WHERE metadata->>'fileName' IS NOT NULL
                    GROUP BY metadata->>'fileName', metadata->>'fileType'
                    ORDER BY MIN(metadata->>'uploadTime') DESC
                    """, (rs, rowNum) -> {
                Map<String, Object> document = new HashMap<>();
                document.put("fileName", rs.getString("file_name"));
                document.put("fileType", rs.getString("file_type"));
                document.put("chunkCount", rs.getInt("chunk_count"));
                document.put("uploadTime", rs.getString("upload_time"));
                return document;
            });
        } catch (Exception e) {
            log.warn("兼容 vector_store 文档列表查询失败", e);
            return new ArrayList<>();
        }
    }

    private Map<String, Object> getLegacyDocumentDetail(String fileName) {
        try {
            List<Map<String, Object>> chunks = jdbcTemplate.query("""
                    SELECT id, content, metadata
                    FROM vector_store
                    WHERE metadata->>'fileName' = ?
                    ORDER BY metadata->>'chunkIndex'
                    """, (rs, rowNum) -> {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("id", rs.getString("id"));
                chunk.put("content", rs.getString("content"));
                return chunk;
            }, fileName);

            Map<String, Object> detail = new HashMap<>();
            detail.put("fileName", fileName);
            detail.put("fileType", fileType(fileName));
            detail.put("chunkCount", chunks.size());
            detail.put("chunks", chunks);
            log.info("兼容文档详情查询完成: {} chunks", chunks.size());
            return detail;
        } catch (Exception e) {
            log.error("查询文档详情失败: {}", fileName, e);
            throw new RuntimeException("查询文档详情失败: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> findChunks(UUID documentId) {
        List<Map<String, Object>> vectorChunks;
        try {
            vectorChunks = jdbcTemplate.query("""
                    SELECT id, content, metadata->>'chunkIndex' AS chunk_index
                    FROM vector_store
                    WHERE metadata->>'documentId' = ?
                    ORDER BY NULLIF(metadata->>'chunkIndex', '')::integer NULLS LAST, id
                    """, (rs, rowNum) -> {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("id", rs.getString("id"));
                chunk.put("content", rs.getString("content"));
                chunk.put("ordinal", rs.getString("chunk_index"));
                chunk.put("indexStatus", "READY");
                return chunk;
            }, documentId.toString());
        } catch (Exception e) {
            log.debug("vector_store chunk 查询失败，回退 document_chunk: {}", documentId, e);
            vectorChunks = new ArrayList<>();
        }
        if (!vectorChunks.isEmpty()) {
            return vectorChunks;
        }

        return jdbcTemplate.query("""
                SELECT id, ordinal, index_status
                FROM document_chunk
                WHERE document_id = ?
                ORDER BY ordinal
                """, (rs, rowNum) -> {
            Map<String, Object> chunk = new HashMap<>();
            chunk.put("id", rs.getObject("id", UUID.class).toString());
            chunk.put("ordinal", rs.getInt("ordinal"));
            chunk.put("indexStatus", rs.getString("index_status"));
            chunk.put("content", "");
            return chunk;
        }, documentId);
    }

    private String fileType(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < fileName.length() - 1
                ? fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private String nullableString(Object value) {
        return value == null ? null : value.toString();
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
