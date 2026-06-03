package com.example.knowledgeagent.document.splitter;

/**
 * 定义 TextChunk 数据结构，用于在层间传递结构化数据。
 *
 * @param splitStrategy 记录本切片采用的边界策略，便于索引后通过 metadata 排查是否发生硬切分。
 */
public record TextChunk(int index, String content, int tokenCount, int startOffset, int endOffset,
                        String splitStrategy) {
    public TextChunk(int index, String content, int tokenCount) {
        this(index, content, tokenCount, -1, -1, "UNKNOWN");
    }

    public TextChunk(int index, String content, int tokenCount, int startOffset, int endOffset) {
        this(index, content, tokenCount, startOffset, endOffset, "PLAIN_TEXT");
    }
}
