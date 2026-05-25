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
/**
 * 定义 OpenAiChatModelService 组件，承载对应模块的业务职责。
 */
public class OpenAiChatModelService implements ChatModelService {
    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 调用 OpenAI-compatible Chat Completions 接口生成完整回答。
     * <p>
     * 未配置 API Key 时返回占位回答，用于本地开发期验证前后端问答流程。
     */
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

    /**
     * 以流式回调形式输出模型回答。
     * <p>
     * 当前实现为了兼容所有 OpenAI-compatible 服务，先复用非流式 chat 获取完整回答，
     * 再切片推送给 SSE 层。后续可替换为真正的 streaming API。
     */
    @Override
    public void streamChat(String prompt, Consumer<String> tokenConsumer) {
        String answer = chat(prompt);
        int step = 24;
        for (int i = 0; i < answer.length(); i += step) {
            tokenConsumer.accept(answer.substring(i, Math.min(i + step, answer.length())));
        }
    }
}
