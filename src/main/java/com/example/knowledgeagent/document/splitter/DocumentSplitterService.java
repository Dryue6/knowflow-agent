package com.example.knowledgeagent.document.splitter;

import java.util.List;

public interface DocumentSplitterService {
    /**
     * 按指定长度和重叠字符数切分文本。
     */
    List<TextChunk> split(String text, int chunkSize, int overlap);
}
