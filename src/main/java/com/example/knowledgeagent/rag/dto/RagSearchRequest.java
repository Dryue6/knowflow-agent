package com.example.knowledgeagent.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record RagSearchRequest(Long knowledgeBaseId, @NotBlank String query, Integer topK, Double minScore) {
}
