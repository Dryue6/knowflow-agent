package com.example.knowledgeagent.rag.vo;

import java.util.List;

/**
 * 定义 RagStreamResult 数据结构，用于在层间传递结构化数据。
 */
public record RagStreamResult(List<CitationVO> citations) {
}
