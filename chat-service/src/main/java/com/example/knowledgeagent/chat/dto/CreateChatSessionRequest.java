package com.example.knowledgeagent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 定义 CreateChatSessionRequest 数据结构，用于在层间传递结构化数据。
 */
public record CreateChatSessionRequest(@NotNull Long knowledgeBaseId, @NotBlank @Size(max = 255) String title) {
}
