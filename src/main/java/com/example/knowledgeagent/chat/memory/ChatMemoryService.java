package com.example.knowledgeagent.chat.memory;

import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;

import java.util.List;

/**
 * 定义 ChatMemoryService 接口，约定该模块对外提供的能力。
 */
public interface ChatMemoryService {
    /**
     * 查询会话最近的历史消息，供 RAG Prompt 构建上下文。
     */
    List<ChatHistoryMessage> recentHistory(Long sessionId, int limit);
}
