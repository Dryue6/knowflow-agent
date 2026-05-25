package com.example.knowledgeagent.document.embedding;

import java.util.List;

/**
 * 定义 EmbeddingService 接口，约定该模块对外提供的能力。
 */
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
