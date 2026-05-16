package com.example.knowledgeagent.chat.vo;

import com.example.knowledgeagent.chat.entity.ChatSession;

import java.time.LocalDateTime;

public record ChatSessionVO(Long id, Long knowledgeBaseId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
    /**
     * 将聊天会话实体转换为前端视图对象。
     */
    public static ChatSessionVO from(ChatSession entity) {
        return new ChatSessionVO(entity.getId(), entity.getKnowledgeBaseId(), entity.getTitle(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
