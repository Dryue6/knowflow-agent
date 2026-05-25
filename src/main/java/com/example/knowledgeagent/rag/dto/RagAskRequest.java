package com.example.knowledgeagent.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 定义 RagAskRequest 数据结构，用于在层间传递结构化数据。
 */
public record RagAskRequest(@NotNull Long knowledgeBaseId, @NotBlank String question, Long sessionId) {
}
