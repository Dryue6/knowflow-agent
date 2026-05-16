package com.example.knowledgeagent.document.service;

public interface DocumentIndexService {
    void indexDocument(Long documentId);

    void deleteDocumentVectors(Long documentId);

    void reindexDocument(Long documentId);
}
