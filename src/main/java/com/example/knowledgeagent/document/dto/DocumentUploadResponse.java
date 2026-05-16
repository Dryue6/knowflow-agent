package com.example.knowledgeagent.document.dto;

import com.example.knowledgeagent.document.enums.DocumentStatus;

public record DocumentUploadResponse(Long documentId, Long jobId, DocumentStatus status) {
}
