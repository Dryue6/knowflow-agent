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
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
/**
 * 定义 OpenAiEmbeddingService 组件，承载对应模块的业务职责。
 */
public class OpenAiEmbeddingService implements EmbeddingService {
    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;

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
     * 如果没有配置 API Key，则使用本地确定性向量作为开发期降级方案，
     * 这样前端和文档索引流程可以在没有真实模型服务时继续联调。
     */
    @Override
    public List<List<Double>> embedTexts(List<String> texts) {
        if (!StringUtils.hasText(properties.embedding().apiKey())) {
            return localEmbeddings(texts);
        }
        try {
            // OpenAI-compatible embedding 接口统一使用 /embeddings，供应商通过配置切换。
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.embedding().modelName(),
                    "input", texts
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.embedding().baseUrl() + "/embeddings"))
                    .timeout(Duration.ofSeconds(properties.embedding().timeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.embedding().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                log.warn("Embedding API returned status {}, fallback to local deterministic embeddings: {}", response.statusCode(), response.body());
                return localEmbeddings(texts);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<List<Double>> result = new ArrayList<>();
            for (JsonNode item : data) {
                List<Double> vector = new ArrayList<>();
                item.path("embedding").forEach(value -> vector.add(value.asDouble()));
                result.add(vector);
            }
            return result;
        } catch (Exception ex) {
            log.warn("Embedding API call failed, fallback to local deterministic embeddings", ex);
            return localEmbeddings(texts);
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
        int dimension = properties.embedding().dimension() == null ? 1536 : properties.embedding().dimension();
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
}
