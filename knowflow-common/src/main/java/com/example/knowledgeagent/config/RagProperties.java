package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
/**
 * 定义 RAG 检索、重写和重排配置。
 *
 * <p>微服务部署依赖 Nacos 配置，但本地或容器启动时可能暂时缺少对应 dataId。这里提供业务可用的默认值，
 * 避免 primitive 字段绑定成 0 后导致 topK、maxContextChunks 等关键参数把检索上下文全部截空。</p>
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
    public RagProperties {
        chunkSize = positive(chunkSize, 1800);
        chunkOverlap = positiveOrZero(chunkOverlap, 300);
        topK = positive(topK, 5);
        minScore = positiveDouble(minScore, 0.8);
        maxContextChunks = positive(maxContextChunks, 6);
        maxHistoryMessages = positive(maxHistoryMessages, 8);
        systemConstraintMaxChunks = positive(systemConstraintMaxChunks, 12);
        pinnedMaxChunks = positive(pinnedMaxChunks, 8);
        candidateTopK = positive(candidateTopK, 30);
        keywordMinScore = positiveDouble(keywordMinScore, 0.2);
        finalMinScore = positiveDouble(finalMinScore, 0.6);
        queryRewrite = queryRewrite == null ? QueryRewrite.defaults() : queryRewrite;
        rerank = rerank == null ? Rerank.defaults() : rerank;
    }

    /**
     * 定义查询改写配置；默认启用 Ollama，并允许通过环境变量覆盖 baseUrl。
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
        public static QueryRewrite defaults() {
            return new QueryRewrite(
                    true,
                    "ollama",
                    "http://ollama-rewrite:11434",
                    "qwen2.5:7b",
                    0.0,
                    90,
                    5,
                    8,
                    96,
                    2048,
                    0,
                    true,
                    60,
                    512,
                    "knowflow:rag:query-rewrite:",
                    true,
                    12,
                    "怎么,如何,为什么,规则,计算,占比,分配,要求,流程",
                    true,
                    "成绩怎么计算",
                    "conservative"
            );
        }
    }

    /**
     * 定义重排配置；默认使用本地轻量重排，避免外部模型不可用时阻断问答链路。
     */
    public record Rerank(
            boolean enabled,
            String mode,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName
    ) {
        public static Rerank defaults() {
            return new Rerank(true, "local", null, null, null, null);
        }
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static int positiveOrZero(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private static double positiveDouble(double value, double fallback) {
        return value > 0 ? value : fallback;
    }
}
