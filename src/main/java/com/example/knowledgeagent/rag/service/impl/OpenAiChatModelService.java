package com.example.knowledgeagent.rag.service.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.AiModelProperties;
import com.example.knowledgeagent.rag.service.ChatModelService;
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

@Service
@RequiredArgsConstructor
public class OpenAiChatModelService implements ChatModelService {
    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String chat(String prompt) {
        if (!StringUtils.hasText(properties.chat().apiKey())) {
            return "当前未配置 Chat Model API Key。已完成知识库检索和 Prompt 构建，请配置 ai.chat 后获取真实模型回答。";
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.chat().modelName(),
                    "temperature", properties.chat().temperature(),
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.chat().baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(properties.chat().timeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.chat().apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_ERROR, "Chat API 调用失败: " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "Chat API 调用失败: " + ex.getMessage());
        }
    }

    @Override
    public void streamChat(String prompt, Consumer<String> tokenConsumer) {
        String answer = chat(prompt);
        int step = 24;
        for (int i = 0; i < answer.length(); i += step) {
            tokenConsumer.accept(answer.substring(i, Math.min(i + step, answer.length())));
        }
    }
}
