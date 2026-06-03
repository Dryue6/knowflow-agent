package com.example.knowledgeagent.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义 LoginRequest 数据结构，用于在层间传递结构化数据。
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
