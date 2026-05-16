package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiModelProperties(Chat chat, Embedding embedding) {

    public record Chat(String baseUrl, String apiKey, String modelName, Double temperature, Integer timeoutSeconds) {
    }

    public record Embedding(String baseUrl, String apiKey, String modelName, Integer dimension, Integer timeoutSeconds) {
    }
}
