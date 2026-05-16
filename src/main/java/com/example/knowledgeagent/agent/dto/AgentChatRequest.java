package com.example.knowledgeagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentChatRequest(@NotNull Long knowledgeBaseId, @NotBlank String question, Long sessionId) {
}
