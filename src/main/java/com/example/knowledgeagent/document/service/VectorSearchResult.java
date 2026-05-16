package com.example.knowledgeagent.document.service;

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
