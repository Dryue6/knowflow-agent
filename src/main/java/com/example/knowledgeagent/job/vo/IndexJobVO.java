package com.example.knowledgeagent.job.vo;

import com.example.knowledgeagent.job.entity.IndexJob;
import com.example.knowledgeagent.job.enums.IndexJobStatus;
import com.example.knowledgeagent.job.enums.IndexJobType;

import java.time.LocalDateTime;

/**
 * 定义 IndexJobVO 数据结构，用于在层间传递结构化数据。
 */
public record IndexJobVO(
        Long id,
        Long documentId,
        Long knowledgeBaseId,
        IndexJobType jobType,
        IndexJobStatus status,
        Integer progress,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 将索引任务实体转换为前端视图对象。
     */
    public static IndexJobVO from(IndexJob entity) {
        return new IndexJobVO(entity.getId(), entity.getDocumentId(), entity.getKnowledgeBaseId(), entity.getJobType(),
                entity.getStatus(), entity.getProgress(), entity.getErrorMessage(), entity.getStartedAt(),
                entity.getFinishedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
