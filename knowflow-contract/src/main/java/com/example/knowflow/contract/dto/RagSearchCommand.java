package com.example.knowflow.contract.dto;

/**
 * RAG 内部检索请求，供其他服务通过 Feign 复用检索能力。
 */
public record RagSearchCommand(
        Long knowledgeBaseId,
        String query,
        Integer topK,
        Double minScore
) {
}
