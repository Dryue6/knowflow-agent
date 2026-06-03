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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Docker 本地环境默认使用 Ollama，本地模型不需要 API Key；外部 OpenAI-compatible
     * 服务仍保留 Bearer Token 调用方式，便于部署时通过环境变量切换。
     */
    @Override
    public String chat(String prompt) {
        AiModelProperties.Chat chat = properties.chat();
        if (chat == null) {
            return "当前未配置 Chat Model API Key。已完成知识库检索和 Prompt 构建，请配置 ai.chat 后获取真实模型回答。";
        }
        if (isOllama(chat)) {
            return chatWithOllama(prompt, chat);
        }
        if (!StringUtils.hasText(chat.apiKey())) {
            return "当前未配置 Chat Model API Key。已完成知识库检索和 Prompt 构建，请配置 ai.chat 后获取真实模型回答。";
        }
        return chatWithOpenAiCompatible(prompt, chat);
    }

    /**
     * 调用 OpenAI-compatible Chat Completions 接口。
     */
    private String chatWithOpenAiCompatible(String prompt, AiModelProperties.Chat chat) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", modelName(chat),
                    "temperature", temperature(chat),
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(chat.baseUrl(), "/chat/completions")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(chat)))
                    .header("Authorization", "Bearer " + chat.apiKey())
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
     * 调用 Ollama 原生 /api/chat 接口。
     * <p>
     * Ollama 不要求 API Key，因此适合作为 Docker 本地开发和私有化部署的默认聊天模型。
     */
    private String chatWithOllama(String prompt, AiModelProperties.Chat chat) {
        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("model", modelName(chat));
            bodyMap.put("stream", false);
            bodyMap.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            bodyMap.put("options", Map.of("temperature", temperature(chat)));
            String body = objectMapper.writeValueAsString(bodyMap);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(chat.baseUrl(), "/api/chat")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(chat)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_ERROR, "Ollama Chat API 调用失败: " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText();
            return StringUtils.hasText(content) ? content : root.path("response").asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "Ollama Chat API 调用失败: " + ex.getMessage());
        }
    }

    /**
     * 根据 baseUrl 判断是否使用 Ollama 原生协议。
     */
    private boolean isOllama(AiModelProperties.Chat chat) {
        String baseUrl = chat.baseUrl();
        return StringUtils.hasText(baseUrl)
                && (baseUrl.contains("ollama") || baseUrl.contains(":11434"));
    }

    /**
     * 拼接模型接口地址，兼容 baseUrl 末尾是否带斜杠。
     */
    private String apiUrl(String baseUrl, String path) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.stripTrailing() : "http://ollama-rewrite:11434";
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + path;
    }

    /**
     * 获取聊天模型名称，缺省使用本地 Ollama 中已拉取的 qwen2.5:7b。
     */
    private String modelName(AiModelProperties.Chat chat) {
        return StringUtils.hasText(chat.modelName()) ? chat.modelName() : "qwen2.5:7b";
    }

    /**
     * 获取模型温度，避免配置缺失时 Map.of 因 null 值失败。
     */
    private double temperature(AiModelProperties.Chat chat) {
        return chat.temperature() == null ? 0.2 : chat.temperature();
    }

    /**
     * 获取模型调用超时时间。
     */
    private int timeoutSeconds(AiModelProperties.Chat chat) {
        return chat.timeoutSeconds() == null || chat.timeoutSeconds() <= 0 ? 60 : chat.timeoutSeconds();
    }

    /**
     * 以真实流式回调形式输出模型回答。
     *
     * <p>外部 DeepSeek/OpenAI-compatible 和本地 Ollama 都支持流式协议；这里直接解析模型
     * 返回的增量片段，避免等待完整回答导致上游 Feign 或 HTTP 请求超时。</p>
     */
    @Override
    public void streamChat(String prompt, Consumer<String> tokenConsumer) {
        AiModelProperties.Chat chat = properties.chat();
        if (chat == null) {
            tokenConsumer.accept("当前未配置 Chat Model API Key。已完成知识库检索和 Prompt 构建，请配置 ai.chat 后获取真实模型回答。");
            return;
        }
        if (isOllama(chat)) {
            streamWithOllama(prompt, chat, tokenConsumer);
            return;
        }
        if (!StringUtils.hasText(chat.apiKey())) {
            tokenConsumer.accept("当前未配置 Chat Model API Key。已完成知识库检索和 Prompt 构建，请配置 ai.chat 后获取真实模型回答。");
            return;
        }
        streamWithOpenAiCompatible(prompt, chat, tokenConsumer);
    }

    /**
     * 解析 OpenAI-compatible SSE 响应，读取 choices[0].delta.content 增量内容。
     */
    private void streamWithOpenAiCompatible(String prompt, AiModelProperties.Chat chat, Consumer<String> tokenConsumer) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", modelName(chat),
                    "temperature", temperature(chat),
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(chat.baseUrl(), "/chat/completions")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(chat)))
                    .header("Authorization", "Bearer " + chat.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() >= 300) {
                    String errorBody = lines.limit(20).collect(Collectors.joining("\n"));
                    throw new BusinessException(ErrorCode.AI_ERROR, "Chat API 流式调用失败: " + errorBody);
                }
                lines.map(this::sseData)
                        .filter(StringUtils::hasText)
                        .takeWhile(data -> !"[DONE]".equals(data))
                        .forEach(data -> emitOpenAiDelta(data, tokenConsumer));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "Chat API 流式调用失败: " + ex.getMessage());
        }
    }

    /**
     * 解析 Ollama 每行一个 JSON 对象的流式响应，读取 message.content 增量内容。
     */
    private void streamWithOllama(String prompt, AiModelProperties.Chat chat, Consumer<String> tokenConsumer) {
        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("model", modelName(chat));
            bodyMap.put("stream", true);
            bodyMap.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            bodyMap.put("options", Map.of("temperature", temperature(chat)));
            String body = objectMapper.writeValueAsString(bodyMap);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(chat.baseUrl(), "/api/chat")))
                    .timeout(Duration.ofSeconds(timeoutSeconds(chat)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() >= 300) {
                    String errorBody = lines.limit(20).collect(Collectors.joining("\n"));
                    throw new BusinessException(ErrorCode.AI_ERROR, "Ollama Chat API 流式调用失败: " + errorBody);
                }
                lines.filter(StringUtils::hasText).forEach(line -> emitOllamaDelta(line, tokenConsumer));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "Ollama Chat API 流式调用失败: " + ex.getMessage());
        }
    }

    /**
     * 提取 SSE data 行；非 data 行不进入模型增量解析。
     */
    private String sseData(String line) {
        if (line == null || !line.startsWith("data:")) {
            return "";
        }
        String data = line.substring("data:".length());
        return data.startsWith(" ") ? data.substring(1) : data;
    }

    /**
     * 将 OpenAI-compatible 增量 JSON 转成前端可见文本。
     */
    private void emitOpenAiDelta(String data, Consumer<String> tokenConsumer) {
        try {
            String content = objectMapper.readTree(data)
                    .path("choices")
                    .path(0)
                    .path("delta")
                    .path("content")
                    .asText("");
            if (StringUtils.hasText(content)) {
                tokenConsumer.accept(content);
            }
        } catch (Exception ignored) {
            // 单个异常增量不应中断整次流式回答，后续 data 行仍可继续解析。
        }
    }

    /**
     * 将 Ollama 增量 JSON 转成前端可见文本。
     */
    private void emitOllamaDelta(String line, Consumer<String> tokenConsumer) {
        try {
            JsonNode root = objectMapper.readTree(line);
            String content = root.path("message").path("content").asText("");
            if (StringUtils.hasText(content)) {
                tokenConsumer.accept(content);
            }
        } catch (Exception ignored) {
            // Ollama 偶发空行或非标准行时跳过，避免影响后续增量。
        }
    }
}
