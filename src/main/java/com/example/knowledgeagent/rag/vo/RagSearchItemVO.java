package com.example.knowledgeagent.rag.vo;

/**
 * 定义 RagSearchItemVO 数据结构，用于在层间传递结构化数据。
 */
public record RagSearchItemVO(Long documentId, String documentName, Long chunkId, Integer chunkIndex, String content, double score) {
}
