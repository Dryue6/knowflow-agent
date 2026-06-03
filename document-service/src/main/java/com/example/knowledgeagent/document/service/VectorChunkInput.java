package com.example.knowledgeagent.document.service;

import java.util.List;

/**
 * 定义 VectorChunkInput 数据结构，用于在层间传递结构化数据。
 */
public record VectorChunkInput(
        Long knowledgeBaseId,
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String content,
        List<Double> embedding,
        String metadataJson
) {
}
