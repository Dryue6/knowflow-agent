package com.example.knowledgeagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 定义 AgentChatRequest 数据结构，用于在层间传递结构化数据。
 */
public record AgentChatRequest(@NotNull Long knowledgeBaseId, @NotBlank String question, Long sessionId) {
}
