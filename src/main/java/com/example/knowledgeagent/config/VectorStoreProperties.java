package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vector")
/**
 * 定义 VectorStoreProperties 数据结构，用于在层间传递结构化数据。
 */
public record VectorStoreProperties(String type) {
}
