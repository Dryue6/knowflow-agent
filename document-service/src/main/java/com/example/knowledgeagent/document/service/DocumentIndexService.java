package com.example.knowledgeagent.document.service;

/**
 * 定义 DocumentIndexService 接口，约定该模块对外提供的能力。
 */
public interface DocumentIndexService {
    /**
     * 对指定文档执行完整索引，并通过 jobId 回写阶段进度。
     */
    void indexDocument(Long jobId, Long documentId);

    /**
     * 删除指定文档的切片向量。
     */
    void deleteDocumentVectors(Long documentId);

    /**
     * 重新执行指定文档索引，复用同一套状态、进度和中止检查逻辑。
     */
    void reindexDocument(Long jobId, Long documentId);
}
