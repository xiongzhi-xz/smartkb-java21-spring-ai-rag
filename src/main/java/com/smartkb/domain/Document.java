package com.smartkb.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档领域模型
 * 表示用户上传的原始文档（PDF、Word、Markdown等）
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /**
     * 文档唯一标识
     */
    private String id;

    /**
     * 文档名称（含扩展名）
     */
    private String fileName;

    /**
     * 文档类型（pdf、docx、md、txt）
     */
    private String fileType;

    /**
     * 文档原始内容（可选，大文档可不存储全文）
     */
    private String content;

    /**
     * 文档元数据（作者、创建时间、标签等）
     */
    private Map<String, Object> metadata;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 处理状态（PENDING, PROCESSING, COMPLETED, FAILED）
     */
    private DocumentStatus status;

    /**
     * 切片数量（处理完成后填充）
     */
    private Integer chunkCount;
}
