package com.example.knowledgeagent.chat.vo;

import com.example.knowledgeagent.chat.entity.ChatSession;

import java.time.LocalDateTime;

public record ChatSessionVO(Long id, Long knowledgeBaseId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ChatSessionVO from(ChatSession entity) {
        return new ChatSessionVO(entity.getId(), entity.getKnowledgeBaseId(), entity.getTitle(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
