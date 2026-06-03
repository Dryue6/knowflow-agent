package com.example.knowledgeagent.document.embedding;

import com.example.knowledgeagent.config.AiModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
/**
 * 定义 OpenAiEmbeddingService 组件，承载对应模块的业务职责。
 */
public class OpenAiEmbeddingService implements EmbeddingService {
    private static final String DEFAULT_EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, List<Double>> localModelCache = new ConcurrentHashMap<>();
    private final AtomicBoolean ollamaModelChecked = new AtomicBoolean(false);

    /**
     * 为单段文本生成 embedding。
     */
    @Override
    public List<Double> embedText(String text) {
        return embedTexts(List.of(text)).get(0);
    }

    /**
     * 批量生成 embedding。
     * <p>
     * Docker 本地环境默认使用 Ollama 的 qwen3-embedding:0.6b 专用向量模型；
     * 外部 OpenAI-compatible embedding 服务仍可通过 baseUrl/apiKey/modelName 切换。
     * Ollama 不需要 API Key，所以不能仅凭 apiKey 为空就退回本地确定性向量。
     */
    @Override
    public List<List<Double>> embedTexts(List<String> texts) {
        AiModelProperties.Embedding embedding = properties.embedding();
        if (embedding == null) {
            return localEmbeddings(texts);
        }
        if (isOllama(embedding)) {
            return ollamaEmbeddings(texts, embedding);
        }
        if (!StringUtils.hasText(embedding.apiKey())) {
            return localEmbeddings(texts);
        }
        try {
            // OpenAI-compatible embedding 接口统一使用 /embeddings，供应商通过配置切换。
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", modelName(embedding),
                    "input", texts
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(embedding.baseUrl(), "/embeddings")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(embedding)))
                    .header("Authorization", "Bearer " + embedding.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                log.warn("Embedding API returned status {}, 检索质量降级为本地确定性向量，model={}, batchSize={}, body={}",
                        response.statusCode(), modelName(embedding), texts.size(), response.body());
                return localEmbeddings(texts);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<List<Double>> result = new ArrayList<>();
            int rawDimension = 0;
            for (JsonNode item : data) {
                List<Double> vector = new ArrayList<>();
                item.path("embedding").forEach(value -> vector.add(value.asDouble()));
                if (rawDimension == 0) {
                    rawDimension = vector.size();
                }
                result.add(fitDimension(vector, embedding));
            }
            log.info("Embedding API 调用成功，provider=openai-compatible, model={}, batchSize={}, rawDimension={}, configuredDimension={}",
                    modelName(embedding), texts.size(), rawDimension, dimension(embedding));
            return result;
        } catch (Exception ex) {
            log.warn("Embedding API call failed，检索质量降级为本地确定性向量，model={}, batchSize={}",
                    modelName(embedding), texts.size(), ex);
            return localEmbeddings(texts);
        }
    }

    /**
     * 提供当前 embedding 配置诊断信息，随 chunk metadata 入库后可用 SQL 排查模型来源。
     */
    @Override
    public Map<String, Object> diagnosticMetadata() {
        AiModelProperties.Embedding embedding = properties.embedding();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("embeddingProvider", provider(embedding));
        metadata.put("embeddingModel", modelName(embedding));
        metadata.put("embeddingConfiguredDimension", dimension(embedding));
        metadata.put("embeddingBaseUrl", embedding == null ? null : embedding.baseUrl());
        return metadata;
    }

    /**
     * 调用 Ollama 原生 embedding 接口。
     * <p>
     * 专用 embedding 模型返回的原始维度可能与当前 pgvector 表定义不同；这里统一裁剪或补零到
     * ai.embedding.dimension，保证索引和查询使用同一维度，避免写入向量表失败。
     */
    private List<List<Double>> ollamaEmbeddings(List<String> texts, AiModelProperties.Embedding embedding) {
        ensureOllamaModelAvailable(embedding);
        List<List<Double>> result = new ArrayList<>(texts.size());
        List<String> missingTexts = new ArrayList<>();
        for (String text : texts) {
            List<Double> cached = localModelCache.get(cacheKey(embedding, text));
            if (cached == null) {
                missingTexts.add(text == null ? "" : text);
            }
        }
        Map<String, List<Double>> loaded = missingTexts.isEmpty() ? Map.of() : loadOllamaEmbeddings(missingTexts, embedding);
        for (String text : texts) {
            String normalizedText = text == null ? "" : text;
            String cacheKey = cacheKey(embedding, normalizedText);
            List<Double> vector = localModelCache.computeIfAbsent(cacheKey, ignored ->
                    loaded.getOrDefault(normalizedText, localEmbedding(normalizedText)));
            result.add(vector);
        }
        return result;
    }

    /**
     * 使用 Ollama /api/embed 批量生成向量。
     * <p>
     * /api/embed 支持数组输入，比逐条调用 /api/embeddings 快很多；切换到专用 embedding
     * 模型后仍保留批处理，避免长文档索引阶段被本地模型推理拖慢。
     */
    private Map<String, List<Double>> loadOllamaEmbeddings(List<String> texts, AiModelProperties.Embedding embedding) {
        long startAt = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", modelName(embedding),
                    "input", texts
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(embedding.baseUrl(), "/api/embed")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(embedding)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                log.warn("Ollama embedding returned status {}，检索质量降级为本地确定性向量，model={}, batchSize={}, elapsedMs={}, body={}",
                        response.statusCode(), modelName(embedding), texts.size(), System.currentTimeMillis() - startAt, response.body());
                return Map.of();
            }
            JsonNode embeddings = objectMapper.readTree(response.body()).path("embeddings");
            Map<String, List<Double>> result = new java.util.LinkedHashMap<>();
            int rawDimension = 0;
            for (int i = 0; i < texts.size() && i < embeddings.size(); i++) {
                List<Double> vector = new ArrayList<>();
                embeddings.get(i).forEach(value -> vector.add(value.asDouble()));
                if (rawDimension == 0) {
                    rawDimension = vector.size();
                }
                result.put(texts.get(i), fitDimension(vector, embedding));
            }
            log.info("Ollama embedding 调用成功，model={}, batchSize={}, loadedCount={}, rawDimension={}, configuredDimension={}, elapsedMs={}, fallback=false",
                    modelName(embedding), texts.size(), result.size(), rawDimension, dimension(embedding),
                    System.currentTimeMillis() - startAt);
            return result;
        } catch (Exception ex) {
            log.warn("Ollama embedding call failed，检索质量降级为本地确定性向量，model={}, batchSize={}, elapsedMs={}, fallback=true",
                    modelName(embedding), texts.size(), System.currentTimeMillis() - startAt, ex);
            return Map.of();
        }
    }

    /**
     * 在首次索引或查询 embedding 前检查 Ollama 是否已拉取目标模型。
     * <p>检查失败不阻断业务，因为开发环境可能临时离线；但日志必须明确提示，否则容易把
     * deterministic fallback 误判为真实语义向量。</p>
     */
    private void ensureOllamaModelAvailable(AiModelProperties.Embedding embedding) {
        if (!ollamaModelChecked.compareAndSet(false, true)) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(embedding.baseUrl(), "/api/tags")))
                    .timeout(Duration.ofSeconds(Math.min(5, timeoutSeconds(embedding))))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                log.warn("Ollama 模型健康检查失败，status={}, model={}, 后续可能降级为本地确定性向量",
                        response.statusCode(), modelName(embedding));
                return;
            }
            String expectedModel = modelName(embedding);
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            for (JsonNode model : models) {
                if (expectedModel.equals(model.path("name").asText())) {
                    log.info("Ollama embedding 模型已就绪，model={}, configuredDimension={}", expectedModel, dimension(embedding));
                    return;
                }
            }
            log.warn("Ollama 未找到配置的 embedding 模型，model={}，请执行 ollama pull {}；后续调用可能超时或降级为本地确定性向量",
                    expectedModel, expectedModel);
        } catch (Exception ex) {
            log.warn("Ollama 模型健康检查异常，model={}，后续可能降级为本地确定性向量: {}",
                    modelName(embedding), ex.getMessage());
        }
    }

    /**
     * 处理 localEmbeddings 方法对应的业务逻辑。
     */
    private List<List<Double>> localEmbeddings(List<String> texts) {
        return texts.stream().map(this::localEmbedding).toList();
    }

    /**
     * 生成本地确定性 embedding。
     * <p>
     * 该向量不具备真实语义能力，只保证同一文本生成同一向量，适合开发环境验证索引、
     * 入库、召回、引用展示等链路。
     */
    private List<Double> localEmbedding(String text) {
        AiModelProperties.Embedding embedding = properties.embedding();
        int dimension = dimension(embedding);
        Random random = new Random(text == null ? 0 : text.hashCode());
        List<Double> vector = new ArrayList<>(dimension);
        double norm = 0;
        for (int i = 0; i < dimension; i++) {
            double value = random.nextDouble() - 0.5;
            vector.add(value);
            norm += value * value;
        }
        double sqrt = Math.sqrt(norm);
        for (int i = 0; i < vector.size(); i++) {
            vector.set(i, vector.get(i) / sqrt);
        }
        return vector;
    }

    /**
     * 判断当前 embedding 配置是否指向 Ollama。
     */
    private boolean isOllama(AiModelProperties.Embedding embedding) {
        String baseUrl = embedding.baseUrl();
        return StringUtils.hasText(baseUrl)
                && (baseUrl.contains("ollama") || baseUrl.contains(":11434"));
    }

    /**
     * 识别当前配置的 embedding 供应商，用于日志和 chunk metadata 排障。
     */
    private String provider(AiModelProperties.Embedding embedding) {
        if (embedding == null) {
            return "local-deterministic";
        }
        if (isOllama(embedding)) {
            return "ollama";
        }
        return StringUtils.hasText(embedding.apiKey()) ? "openai-compatible" : "local-deterministic";
    }

    /**
     * 将模型返回向量调整到数据库配置维度，并做 L2 归一化，便于 cosine 距离稳定比较。
     */
    private List<Double> fitDimension(List<Double> rawVector, AiModelProperties.Embedding embedding) {
        int dimension = dimension(embedding);
        List<Double> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            vector.add(i < rawVector.size() ? rawVector.get(i) : 0.0);
        }
        double norm = 0;
        for (Double value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return vector;
        }
        double sqrt = Math.sqrt(norm);
        for (int i = 0; i < vector.size(); i++) {
            vector.set(i, vector.get(i) / sqrt);
        }
        return vector;
    }

    /**
     * 拼接模型接口地址，兼容 baseUrl 末尾是否带斜杠。
     */
    private String apiUrl(String baseUrl, String path) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://localhost:11434";
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + path;
    }

    /**
     * 读取 embedding 模型名称，本地默认使用专用向量模型 qwen3-embedding:0.6b。
     */
    private String modelName(AiModelProperties.Embedding embedding) {
        return embedding != null && StringUtils.hasText(embedding.modelName()) ? embedding.modelName() : DEFAULT_EMBEDDING_MODEL;
    }

    /**
     * 读取向量维度，保持与 document_vector 表定义一致。
     */
    private int dimension(AiModelProperties.Embedding embedding) {
        return embedding == null || embedding.dimension() == null || embedding.dimension() <= 0 ? 1024 : embedding.dimension();
    }

    /**
     * 读取模型调用超时时间。
     */
    private int timeoutSeconds(AiModelProperties.Embedding embedding) {
        return embedding.timeoutSeconds() == null || embedding.timeoutSeconds() <= 0 ? 60 : embedding.timeoutSeconds();
    }

    /**
     * 生成本地模型 embedding 缓存 key，同一模型、维度、文本复用向量，减少重复推理。
     */
    private String cacheKey(AiModelProperties.Embedding embedding, String text) {
        return modelName(embedding) + ":" + dimension(embedding) + ":" + (text == null ? "" : text);
    }
}
