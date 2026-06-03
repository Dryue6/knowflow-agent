package com.example.knowflow.contract.dto;

import java.util.List;

/**
 * RAG 检索结果，避免服务间直接复用 rag-service 的前端 VO。
 */
public record RagSearchResult(
        String query,
        List<RagSearchItem> chunks
) {
}
