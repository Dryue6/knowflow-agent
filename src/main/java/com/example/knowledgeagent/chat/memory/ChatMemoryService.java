package com.example.knowledgeagent.chat.memory;

import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;

import java.util.List;

public interface ChatMemoryService {
    List<ChatHistoryMessage> recentHistory(Long sessionId, int limit);
}
