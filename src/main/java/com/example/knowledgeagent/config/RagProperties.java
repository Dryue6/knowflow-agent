package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(int chunkSize, int chunkOverlap, int topK, double minScore, int maxContextChunks, int maxHistoryMessages) {
}
