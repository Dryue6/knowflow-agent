package com.example.knowledgeagent.storage;

/**
 * 定义 StoredFile 数据结构，用于在层间传递结构化数据。
 */
public record StoredFile(String fileName, String originalFileName, String filePath, long size) {
}
