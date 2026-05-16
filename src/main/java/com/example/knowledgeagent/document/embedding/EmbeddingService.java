package com.example.knowledgeagent.document.embedding;

import java.util.List;

public interface EmbeddingService {
    /**
     * 生成单段文本向量。
     */
    List<Double> embedText(String text);

    /**
     * 批量生成文本向量。
     */
    List<List<Double>> embedTexts(List<String> texts);
}
