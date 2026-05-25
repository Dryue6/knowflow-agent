package com.example.knowledgeagent.document.vo;

import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.enums.DocumentConstraintLevel;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;

import java.time.LocalDateTime;

/**
 * 定义 DocumentVO 数据结构，用于在层间传递结构化数据。
 */
public record DocumentVO(
        Long id,
        Long knowledgeBaseId,
        String fileName,
        String originalFileName,
        FileType fileType,
        Long fileSize,
        String title,
        DocumentStatus status,
        String errorMessage,
        Integer chunkCount,
        DocumentConstraintLevel constraintLevel,
        Integer constraintPriority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 将文档实体转换为前端视图对象。
     */
    public static DocumentVO from(Document entity) {
        return new DocumentVO(entity.getId(), entity.getKnowledgeBaseId(), entity.getFileName(), entity.getOriginalFileName(),
                entity.getFileType(), entity.getFileSize(), entity.getTitle(), entity.getStatus(), entity.getErrorMessage(),
                entity.getChunkCount(), entity.getConstraintLevel(), entity.getConstraintPriority(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
