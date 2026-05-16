package com.example.knowledgeagent.document.service;

import java.util.List;

public interface VectorStoreService {
    List<String> upsertChunks(List<VectorChunkInput> chunks);

    List<VectorSearchResult> searchSimilarChunks(Long knowledgeBaseId, List<Double> queryEmbedding, int topK, double minScore);

    void deleteByDocumentId(Long documentId);

    void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}
