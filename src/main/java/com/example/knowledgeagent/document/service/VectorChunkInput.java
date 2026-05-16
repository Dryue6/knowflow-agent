package com.example.knowledgeagent.document.service;

import java.util.List;

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
