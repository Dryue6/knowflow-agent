package com.example.knowledgeagent.document.vo;

import com.example.knowledgeagent.document.entity.DocumentChunk;

import java.time.LocalDateTime;

public record DocumentChunkVO(
        Long id,
        Long documentId,
        Integer chunkIndex,
        String content,
        Integer tokenCount,
        String vectorId,
        LocalDateTime createdAt
) {
    public static DocumentChunkVO from(DocumentChunk entity) {
        return new DocumentChunkVO(entity.getId(), entity.getDocumentId(), entity.getChunkIndex(), entity.getContent(),
                entity.getTokenCount(), entity.getVectorId(), entity.getCreatedAt());
    }
}
