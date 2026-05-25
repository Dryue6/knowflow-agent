package com.example.knowledgeagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 定义 RegisterRequest 数据结构，用于在层间传递结构化数据。
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 6, max = 128) String password,
        @Size(max = 64) String displayName
) {
}
