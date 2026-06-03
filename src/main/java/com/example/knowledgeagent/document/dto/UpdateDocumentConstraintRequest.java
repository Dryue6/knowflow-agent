package com.example.knowledgeagent.document.dto;

import com.example.knowledgeagent.document.enums.DocumentConstraintLevel;
import jakarta.validation.constraints.NotNull;

/**
 * 定义 UpdateDocumentConstraintRequest 数据结构，用于在层间传递结构化数据。
 */
public record UpdateDocumentConstraintRequest(
        @NotNull DocumentConstraintLevel constraintLevel,
        Integer constraintPriority
) {
}
