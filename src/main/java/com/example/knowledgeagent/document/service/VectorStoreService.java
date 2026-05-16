package com.example.knowledgeagent.document.service;

import java.util.List;

public interface VectorStoreService {
    /**
     * 批量写入切片向量，返回向量库主键列表。
     */
    List<String> upsertChunks(List<VectorChunkInput> chunks);

    /**
     * 按知识库过滤并检索相似切片。
     */
    List<VectorSearchResult> searchSimilarChunks(Long knowledgeBaseId, List<Double> queryEmbedding, int topK, double minScore);

    /**
     * 删除指定文档的向量。
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 删除指定知识库的向量。
     */
    void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}
