package com.example.knowledgeagent.job.vo;

import com.example.knowledgeagent.job.entity.IndexJob;
import com.example.knowledgeagent.job.enums.IndexJobStatus;
import com.example.knowledgeagent.job.enums.IndexJobType;

import java.time.LocalDateTime;

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
    public static IndexJobVO from(IndexJob entity) {
        return new IndexJobVO(entity.getId(), entity.getDocumentId(), entity.getKnowledgeBaseId(), entity.getJobType(),
                entity.getStatus(), entity.getProgress(), entity.getErrorMessage(), entity.getStartedAt(),
                entity.getFinishedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
