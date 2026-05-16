package com.example.knowledgeagent.document.embedding;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.AiModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OpenAiEmbeddingService implements EmbeddingService {
    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Double> embedText(String text) {
        return embedTexts(List.of(text)).get(0);
    }

    @Override
    public List<List<Double>> embedTexts(List<String> texts) {
        if (!StringUtils.hasText(properties.embedding().apiKey())) {
            return texts.stream().map(this::localEmbedding).toList();
        }
        try {
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
                throw new BusinessException(ErrorCode.AI_ERROR, "Embedding API 调用失败: " + response.body());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<List<Double>> result = new ArrayList<>();
            for (JsonNode item : data) {
                List<Double> vector = new ArrayList<>();
                item.path("embedding").forEach(value -> vector.add(value.asDouble()));
                result.add(vector);
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "Embedding API 调用失败: " + ex.getMessage());
        }
    }

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
