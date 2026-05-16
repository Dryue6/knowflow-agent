package com.example.knowledgeagent.chat.memory;

import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;

import java.util.List;

public interface ChatMemoryService {
    /**
     * 查询会话最近的历史消息，供 RAG Prompt 构建上下文。
     */
    List<ChatHistoryMessage> recentHistory(Long sessionId, int limit);
}
