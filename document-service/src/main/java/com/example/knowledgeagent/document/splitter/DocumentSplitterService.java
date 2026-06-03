package com.example.knowledgeagent.document.splitter;

import java.util.List;

/**
 * 定义 DocumentSplitterService 接口，约定该模块对外提供的能力。
 */
public interface DocumentSplitterService {
    /**
     * 按指定长度和重叠字符数切分文本。
     */
    List<TextChunk> split(String text, int chunkSize, int overlap);
}
