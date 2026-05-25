package com.example.knowledgeagent.knowledge.vo;

import com.example.knowledgeagent.knowledge.entity.KnowledgeBase;
import com.example.knowledgeagent.knowledge.enums.KnowledgeBaseStatus;

import java.time.LocalDateTime;

/**
 * 定义 KnowledgeBaseVO 数据结构，用于在层间传递结构化数据。
 */
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
    /**
     * 将知识库实体转换为前端视图对象。
     */
    public static KnowledgeBaseVO from(KnowledgeBase entity) {
        return new KnowledgeBaseVO(entity.getId(), entity.getName(), entity.getDescription(), entity.getStatus(),
                entity.getDocumentCount(), entity.getChunkCount(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
