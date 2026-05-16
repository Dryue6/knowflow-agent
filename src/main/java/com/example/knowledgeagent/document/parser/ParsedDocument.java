package com.example.knowledgeagent.document.parser;

import java.util.Map;

public record ParsedDocument(String title, String text, Map<String, Object> metadata) {
}
