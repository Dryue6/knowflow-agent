package com.example.knowledgeagent.document.vo;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * 定义 DocumentFileResource 数据结构，用于在层间传递结构化数据。
 */
public record DocumentFileResource(
        Resource resource,
        String fileName,
        MediaType mediaType,
        long contentLength
) {
}
