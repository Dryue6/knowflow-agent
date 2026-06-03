package com.example.knowflow.contract.dto;

/**
 * RAG 检索命中的切片摘要，作为跨服务稳定响应结构。
 */
public record RagSearchItem(
        Long documentId,
        String documentName,
        Long chunkId,
        Integer chunkIndex,
        String content,
        Double score
) {
}
