package com.example.knowledgeagent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(@NotNull Long knowledgeBaseId, @NotBlank @Size(max = 255) String title) {
}
