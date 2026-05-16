package com.example.knowledgeagent.storage;

public record StoredFile(String fileName, String originalFileName, String filePath, long size) {
}
