package com.example.knowledgeagent.rag.vo;

/**
 * 定义 CitationVO 数据结构，用于在层间传递结构化数据。
 */
public record CitationVO(
        Long documentId,
        String documentName,
        Long chunkId,
        Integer chunkIndex,
        String contentPreview,
        double score,
        Integer pageNumber,
        String sectionTitle,
        Integer paragraphIndex,
        String locationText
) {
}
