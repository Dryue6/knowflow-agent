package com.example.knowledgeagent.chat.vo;

import com.example.knowledgeagent.chat.entity.ChatMessage;
import com.example.knowledgeagent.chat.enums.ChatRole;

import java.time.LocalDateTime;

public record ChatMessageVO(Long id, Long sessionId, ChatRole role, String content, String citationsJson, LocalDateTime createdAt) {
    public static ChatMessageVO from(ChatMessage entity) {
        return new ChatMessageVO(entity.getId(), entity.getSessionId(), entity.getRole(), entity.getContent(), entity.getCitationsJson(), entity.getCreatedAt());
    }
}
