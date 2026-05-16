package com.example.knowledgeagent.rag.vo;

public record CitationVO(Long documentId, String documentName, Long chunkId, Integer chunkIndex, String contentPreview, double score) {
}
