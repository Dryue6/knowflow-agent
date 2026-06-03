package com.example.knowflow.contract.dto;

import java.time.LocalDateTime;

/**
 * 知识库内部服务信息，避免 Feign 契约直接依赖 knowledge-service 的展示层 VO。
 */
public record KnowledgeBaseInfo(
        Long id,
        String name,
        String description,
        String status,
        Integer documentCount,
        Integer chunkCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
