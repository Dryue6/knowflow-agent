package com.example.knowflow.contract.dto;

import java.util.List;

/**
 * RAG 非流式问答结果，供 chat-service 和 agent-service 内部调用。
 */
public record RagAnswerResult(
        String answer,
        List<CitationItem> citations
) {
}
