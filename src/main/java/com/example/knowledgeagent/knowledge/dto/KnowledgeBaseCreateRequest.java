package com.example.knowledgeagent.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseCreateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 2000) String description
) {
}
