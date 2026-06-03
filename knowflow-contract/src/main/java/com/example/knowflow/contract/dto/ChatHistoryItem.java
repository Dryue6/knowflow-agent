package com.example.knowflow.contract.dto;

/**
 * 服务间传递的聊天历史消息，供 chat-service 调用 rag-service 时补充上下文。
 */
public record ChatHistoryItem(
        String role,
        String content
) {
}
