package com.example.knowledgeagent.document.vo;

import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;

import java.time.LocalDateTime;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocumentVO from(Document entity) {
        return new DocumentVO(entity.getId(), entity.getKnowledgeBaseId(), entity.getFileName(), entity.getOriginalFileName(),
                entity.getFileType(), entity.getFileSize(), entity.getTitle(), entity.getStatus(), entity.getErrorMessage(),
                entity.getChunkCount(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
