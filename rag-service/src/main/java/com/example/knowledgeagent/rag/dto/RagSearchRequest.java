package com.example.knowledgeagent.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义 RagSearchRequest 数据结构，用于在层间传递结构化数据。
 */
public record RagSearchRequest(Long knowledgeBaseId, @NotBlank String query, Integer topK, Double minScore) {
}
