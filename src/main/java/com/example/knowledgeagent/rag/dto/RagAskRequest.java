package com.example.knowledgeagent.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RagAskRequest(@NotNull Long knowledgeBaseId, @NotBlank String question, Long sessionId) {
}
