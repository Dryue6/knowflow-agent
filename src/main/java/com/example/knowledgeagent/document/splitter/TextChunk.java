package com.example.knowledgeagent.document.splitter;

/**
 * 定义 TextChunk 数据结构，用于在层间传递结构化数据。
 */
public record TextChunk(int index, String content, int tokenCount, int startOffset, int endOffset) {
    public TextChunk(int index, String content, int tokenCount) {
        this(index, content, tokenCount, -1, -1);
    }
}
