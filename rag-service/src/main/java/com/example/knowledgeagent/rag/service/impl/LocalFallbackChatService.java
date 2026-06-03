package com.example.knowledgeagent.rag.service.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.RagProperties;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地兜底聊天服务。
 *
 * <p>当知识库没有命中可引用资料时，RAG 不再调用 DeepSeek API，而是使用查询改写同一套
 * Ollama qwen2.5 配置生成通用回答，保证“无资料可引用”和“本地模型兜底”两个语义都清晰。</p>
 */
@Service
@RequiredArgsConstructor
public class LocalFallbackChatService {
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    /**
     * 调用 Ollama /api/chat 生成完整回答。
     */
    public String chat(String prompt) {
        RagProperties.QueryRewrite config = ragProperties.queryRewrite();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", modelName(config),
                    "stream", false,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    // 无知识库命中时只需要简洁通用回答，限制输出长度可以显著降低本地模型等待时间。
                    "options", Map.of("temperature", config.temperature(), "num_predict", config.numPredict(), "num_ctx", config.numCtx())
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(config.baseUrl(), "/api/chat")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(config)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_ERROR, "本地兜底模型调用失败: " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText();
            return StringUtils.hasText(content) ? content : root.path("response").asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "本地兜底模型调用失败: " + ex.getMessage());
        }
    }

    /**
     * 以 Ollama 原生流式响应输出本地兜底回答。
     */
    public void streamChat(String prompt, Consumer<String> tokenConsumer) {
        RagProperties.QueryRewrite config = ragProperties.queryRewrite();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", modelName(config),
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    // 本地兜底优先快速给出通用回答，因此沿用查询改写的输出长度限制。
                    "options", Map.of("temperature", config.temperature(), "num_predict", config.numPredict(), "num_ctx", config.numCtx())
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(config.baseUrl(), "/api/chat")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(config)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() >= 300) {
                    String errorBody = lines.limit(20).collect(Collectors.joining("\n"));
                    throw new BusinessException(ErrorCode.AI_ERROR, "本地兜底模型流式调用失败: " + errorBody);
                }
                lines.filter(StringUtils::hasText).forEach(line -> emitOllamaDelta(line, tokenConsumer));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "本地兜底模型流式调用失败: " + ex.getMessage());
        }
    }

    /**
     * 解析 Ollama 每行 JSON 中的增量文本。
     */
    private void emitOllamaDelta(String line, Consumer<String> tokenConsumer) {
        try {
            String content = objectMapper.readTree(line).path("message").path("content").asText("");
            if (StringUtils.hasText(content)) {
                tokenConsumer.accept(content);
            }
        } catch (Exception ignored) {
            // 空行或非标准行直接跳过，后续增量仍可继续解析。
        }
    }

    /**
     * 拼接 Ollama 接口地址，兼容容器内地址和本机地址。
     */
    private String apiUrl(String baseUrl, String path) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://ollama-rewrite:11434";
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + path;
    }

    /**
     * 读取本地兜底模型名称，默认与查询改写保持一致。
     */
    private String modelName(RagProperties.QueryRewrite config) {
        return StringUtils.hasText(config.modelName()) ? config.modelName() : "qwen2.5:7b";
    }

    /**
     * 读取本地模型调用超时，避免使用不可用配置导致请求无限等待。
     */
    private int timeoutSeconds(RagProperties.QueryRewrite config) {
        return config.timeoutSeconds() <= 0 ? 90 : config.timeoutSeconds();
    }
}
