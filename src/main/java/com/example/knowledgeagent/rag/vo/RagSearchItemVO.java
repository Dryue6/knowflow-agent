package com.example.knowledgeagent.rag.vo;

public record RagSearchItemVO(Long documentId, String documentName, Long chunkId, Integer chunkIndex, String content, double score) {
}
