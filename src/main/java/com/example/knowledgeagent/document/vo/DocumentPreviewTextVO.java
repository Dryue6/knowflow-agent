package com.example.knowledgeagent.document.vo;

import com.example.knowledgeagent.document.enums.FileType;

/**
 * 定义 DocumentPreviewTextVO 数据结构，用于在层间传递结构化数据。
 */
public record DocumentPreviewTextVO(
        Long documentId,
        String fileName,
        FileType fileType,
        String content,
        String previewMode
) {
}
