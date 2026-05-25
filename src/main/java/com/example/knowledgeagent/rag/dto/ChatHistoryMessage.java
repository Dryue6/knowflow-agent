package com.example.knowledgeagent.rag.dto;

/**
 * 定义 ChatHistoryMessage 数据结构，用于在层间传递结构化数据。
 */
public record ChatHistoryMessage(String role, String content) {
}
