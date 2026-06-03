package com.example.knowflow.contract.dto;

import java.util.List;

/**
 * 文档服务关键词召回请求，RAG 服务只传递重写后的关键词集合。
 */
public record DocumentKeywordSearchCommand(
        Long knowledgeBaseId,
        List<String> phraseTerms,
        List<String> coreTerms,
        List<String> expandedTerms,
        Integer topK,
        Double minScore
) {
}
