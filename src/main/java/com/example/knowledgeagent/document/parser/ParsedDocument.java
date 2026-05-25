package com.example.knowledgeagent.document.parser;

import java.util.Map;

/**
 * 定义 ParsedDocument 数据结构，用于在层间传递结构化数据。
 */
public record ParsedDocument(String title, String text, Map<String, Object> metadata) {
}
