package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
/**
 * 定义 RagProperties 数据结构，用于在层间传递结构化数据。
 */
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int topK,
        double minScore,
        int maxContextChunks,
        int maxHistoryMessages,
        int systemConstraintMaxChunks,
        int pinnedMaxChunks,
        int candidateTopK,
        double keywordMinScore,
        double finalMinScore,
        QueryRewrite queryRewrite,
        Rerank rerank
) {
    /**
     * 定义 QueryRewrite 数据结构，用于在层间传递结构化数据。
     */
    public record QueryRewrite(
            boolean enabled,
            String provider,
            String baseUrl,
            String modelName,
            double temperature,
            int timeoutSeconds,
            int maxQueryVariants,
            int maxTermsPerField,
            int numPredict,
            int numCtx,
            int maxRetries,
            boolean cacheEnabled,
            int cacheTtlMinutes,
            int cacheMaxSize,
            String cacheKeyPrefix,
            boolean fastPathEnabled,
            int fastPathMaxLength,
            String complexIntentKeywords,
            boolean warmupEnabled,
            String warmupQuery,
            String fallbackMode
    ) {
    }

    /**
     * 定义 Rerank 数据结构，用于在层间传递结构化数据。
     */
    public record Rerank(
            boolean enabled,
            String mode,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName
    ) {
    }
}
