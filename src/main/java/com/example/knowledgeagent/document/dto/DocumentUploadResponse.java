package com.example.knowledgeagent.document.dto;

import com.example.knowledgeagent.document.enums.DocumentStatus;

/**
 * 定义 DocumentUploadResponse 数据结构，用于在层间传递结构化数据。
 */
public record DocumentUploadResponse(Long documentId, Long jobId, DocumentStatus status) {
}
