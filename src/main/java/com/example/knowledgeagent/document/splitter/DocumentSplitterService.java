package com.example.knowledgeagent.document.splitter;

import java.util.List;

public interface DocumentSplitterService {
    List<TextChunk> split(String text, int chunkSize, int overlap);
}
