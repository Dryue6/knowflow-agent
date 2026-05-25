package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
/**
 * 定义 AiModelProperties 数据结构，用于在层间传递结构化数据。
 */
public record AiModelProperties(Chat chat, Embedding embedding) {

    /**
     * 定义 Chat 数据结构，用于在层间传递结构化数据。
     */
    public record Chat(String baseUrl, String apiKey, String modelName, Double temperature, Integer timeoutSeconds) {
    }

    /**
     * 定义 Embedding 数据结构，用于在层间传递结构化数据。
     */
    public record Embedding(String baseUrl, String apiKey, String modelName, Integer dimension, Integer timeoutSeconds) {
    }
}
