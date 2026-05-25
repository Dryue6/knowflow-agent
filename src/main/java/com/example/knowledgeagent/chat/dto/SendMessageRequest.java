package com.example.knowledgeagent.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义 SendMessageRequest 数据结构，用于在层间传递结构化数据。
 */
public record SendMessageRequest(@NotBlank String content) {
}
