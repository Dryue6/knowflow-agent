package com.example.knowledgeagent.document.service;

public interface DocumentIndexService {
    /**
     * 对指定文档执行完整索引。
     */
    void indexDocument(Long documentId);

    /**
     * 删除指定文档的切片向量。
     */
    void deleteDocumentVectors(Long documentId);

    /**
     * 重新执行指定文档索引。
     */
    void reindexDocument(Long documentId);
}
