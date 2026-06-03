package com.example.knowflow.contract.dto;

import java.util.List;

/**
 * RAG 内部问答请求，包含问题、知识库和调用方整理好的历史消息。
 */
public record RagAskCommand(
        Long knowledgeBaseId,
        String question,
        Long sessionId,
        List<ChatHistoryItem> history
) {
}
