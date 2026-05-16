package com.example.knowledgeagent.knowledge.vo;

import com.example.knowledgeagent.knowledge.entity.KnowledgeBase;
import com.example.knowledgeagent.knowledge.enums.KnowledgeBaseStatus;

import java.time.LocalDateTime;

public record KnowledgeBaseVO(
        Long id,
        String name,
        String description,
        KnowledgeBaseStatus status,
        Integer documentCount,
        Integer chunkCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static KnowledgeBaseVO from(KnowledgeBase entity) {
        return new KnowledgeBaseVO(entity.getId(), entity.getName(), entity.getDescription(), entity.getStatus(),
                entity.getDocumentCount(), entity.getChunkCount(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
