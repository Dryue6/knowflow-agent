package com.example.knowflow.contract.dto;

/**
 * RAG 引用来源，作为 chat-service 和 agent-service 展示引用的稳定契约。
 */
public record CitationItem(
        Long documentId,
        String documentName,
        Long chunkId,
        Integer chunkIndex,
        String snippet,
        Double score,
        Integer pageNumber,
        String sectionTitle,
        Integer paragraphIndex
) {
}
