package com.example.knowledgeagent.document.service;

/**
 * 定义 VectorSearchResult 数据结构，用于在层间传递结构化数据。
 */
public record VectorSearchResult(
        String vectorId,
        Long knowledgeBaseId,
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String content,
        double score
) {
}
