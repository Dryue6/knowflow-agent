package com.example.knowledgeagent.document.embedding;

import java.util.List;
import java.util.Map;

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

    /**
     * 返回当前 embedding 配置的诊断元数据。
     * <p>索引写入 chunk metadata 时会使用这些字段，方便后续排查某批切片使用的模型、
     * 维度和供应商；默认空实现用于兼容未来的其他 EmbeddingService 实现。</p>
     */
    default Map<String, Object> diagnosticMetadata() {
        return Map.of();
    }
}
