package com.example.knowledgeagent.chat.vo;

import com.example.knowledgeagent.chat.entity.ChatMessage;
import com.example.knowledgeagent.chat.enums.ChatRole;

import java.time.LocalDateTime;

/**
 * 定义 ChatMessageVO 数据结构，用于在层间传递结构化数据。
 */
public record ChatMessageVO(Long id, Long sessionId, ChatRole role, String content, String citationsJson, LocalDateTime createdAt) {
    /**
     * 将聊天消息实体转换为前端视图对象。
     */
    public static ChatMessageVO from(ChatMessage entity) {
        return new ChatMessageVO(entity.getId(), entity.getSessionId(), entity.getRole(), entity.getContent(), entity.getCitationsJson(), entity.getCreatedAt());
    }
}
