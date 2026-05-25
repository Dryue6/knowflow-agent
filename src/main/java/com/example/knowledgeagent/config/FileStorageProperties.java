package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.local")
/**
 * 定义 FileStorageProperties 数据结构，用于在层间传递结构化数据。
 */
public record FileStorageProperties(String basePath) {
}
