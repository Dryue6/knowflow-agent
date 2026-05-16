package com.example.knowledgeagent.knowledge.dto;

import com.example.knowledgeagent.knowledge.enums.KnowledgeBaseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 2000) String description,
        KnowledgeBaseStatus status
) {
}
