package com.example.knowledgeagent.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowledgeagent.chat.dto.CreateChatSessionRequest;
import com.example.knowledgeagent.chat.dto.SendMessageRequest;
import com.example.knowledgeagent.chat.entity.ChatMessage;
import com.example.knowledgeagent.chat.entity.ChatSession;
import com.example.knowledgeagent.chat.enums.ChatRole;
import com.example.knowledgeagent.chat.mapper.ChatMessageMapper;
import com.example.knowledgeagent.chat.mapper.ChatSessionMapper;
import com.example.knowledgeagent.chat.memory.ChatMemoryService;
import com.example.knowledgeagent.chat.service.ChatService;
import com.example.knowledgeagent.chat.vo.ChatMessageVO;
import com.example.knowledgeagent.chat.vo.ChatReplyVO;
import com.example.knowledgeagent.chat.vo.ChatSessionVO;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.ChatProperties;
import com.example.knowledgeagent.config.RagProperties;
import com.example.knowflow.contract.client.RagClient;
import com.example.knowflow.contract.dto.CitationItem;
import com.example.knowflow.contract.dto.RagAnswerResult;
import com.example.knowflow.contract.dto.RagAskCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
/**
 * 定义 ChatServiceImpl 组件，承载对应模块的业务职责。
 */
public class ChatServiceImpl implements ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemoryService chatMemoryService;
    private final RagClient ragClient;
    private final RagProperties ragProperties;
    private final ChatProperties chatProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 创建聊天会话，并绑定到指定知识库。
     */
    @Override
    @Transactional
    public ChatSessionVO createSession(CreateChatSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setKnowledgeBaseId(request.knowledgeBaseId());
        session.setTitle(request.title());
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        chatSessionMapper.insert(session);
        return ChatSessionVO.from(session);
    }

    /**
     * 分页查询聊天会话，可按知识库过滤。
     */
    @Override
    public PageResult<ChatSessionVO> listSessions(Long knowledgeBaseId, long page, long size) {
        Page<ChatSession> result = chatSessionMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ChatSession>()
                .eq(knowledgeBaseId != null, ChatSession::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(ChatSession::getUpdatedAt));
        return PageResult.of(result.getRecords().stream().map(ChatSessionVO::from).toList(), result.getTotal(), page, size);
    }

    /**
     * 查询单个聊天会话详情。
     */
    @Override
    public ChatSessionVO getSession(Long sessionId) {
        return ChatSessionVO.from(mustGet(sessionId));
    }

    /**
     * 删除聊天会话及其全部消息记录。
     */
    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        mustGet(sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 分页查询会话消息，按创建时间正序返回，便于前端直接渲染聊天记录。
     */
    @Override
    public PageResult<ChatMessageVO> getMessages(Long sessionId, long page, long size) {
        mustGet(sessionId);
        Page<ChatMessage> result = chatMessageMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt));
        return PageResult.of(result.getRecords().stream().map(this::toMessageVO).toList(), result.getTotal(), page, size);
    }

    /**
     * 发送非流式消息。
     * <p>
     * 会先保存用户消息，再读取最近 N 条历史消息作为上下文，调用 RAG 生成答案，
     * 最后保存助手消息和引用来源 JSON。
     */
    @Override
    @Transactional
    public ChatReplyVO sendMessage(Long sessionId, SendMessageRequest request) {
        ChatSession session = mustGet(sessionId);
        ChatMessage userMessage = saveMessage(sessionId, ChatRole.USER, request.content(), null);
        RagAnswerResult answer = askRag(session, request);
        ChatMessage assistantMessage = saveMessage(sessionId, ChatRole.ASSISTANT, answer.answer(), toJson(answer.citations()));
        touchSession(session);
        return new ChatReplyVO(userMessage.getId(), assistantMessage.getId(), answer.answer(), answer.citations());
    }

    /**
     * 发送 SSE 流式消息。
     * <p>
     * 由于 SSE 响应需要在请求线程返回后持续推送数据，这里使用异步任务调用 RAG 流式接口。
     * token 会通过 `message` 事件实时发送，完成后再发送助手消息 ID 和 citations。
     */
    @Override
    public SseEmitter sendMessageStream(Long sessionId, SendMessageRequest request) {
        ChatSession session = mustGet(sessionId);
        ChatMessage userMessage = saveMessage(sessionId, ChatRole.USER, request.content(), null);
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            try {
                // 先把用户消息 ID 推给前端，前端可立即把本轮问题和后端记录关联起来。
                send(emitter, "userMessageId", userMessage.getId());
                List<CitationItem> citations = streamRag(session, request, token -> {
                    answer.append(token);
                    send(emitter, "message", token);
                });
                // RAG 流结束后再保存助手消息，避免异常中断时写入半截回答。
                ChatMessage assistant = saveMessage(sessionId, ChatRole.ASSISTANT, answer.toString(), toJson(citations));
                touchSession(session);
                send(emitter, "assistantMessageId", assistant.getId());
                send(emitter, "citations", citations);
                emitter.complete();
            } catch (Exception ex) {
                sendError(emitter, ex);
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 通过 Feign 调用 rag-service，chat-service 不再直接注入 RAG 本地实现。
     */
    private RagAnswerResult askRag(ChatSession session, SendMessageRequest request) {
        var response = ragClient.ask(new RagAskCommand(
                session.getKnowledgeBaseId(),
                request.content(),
                session.getId(),
                chatMemoryService.recentHistory(session.getId(), ragProperties.maxHistoryMessages())));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            throw new BusinessException(ErrorCode.AI_ERROR, response.message());
        }
        return response.data();
    }

    /**
     * 调用 rag-service 的内部 SSE 接口，并把模型增量直接转发给前端。
     *
     * <p>这里绕开 Feign 非流式调用，是为了让 DeepSeek/Ollama 的首个 token 能尽快到达浏览器，
     * 同时避免等待完整回答时触发 HTTP read timeout。</p>
     */
    private List<CitationItem> streamRag(ChatSession session, SendMessageRequest request, Consumer<String> tokenConsumer) {
        try {
            String body = objectMapper.writeValueAsString(new RagAskCommand(
                    session.getKnowledgeBaseId(),
                    request.content(),
                    session.getId(),
                    chatMemoryService.recentHistory(session.getId(), ragProperties.maxHistoryMessages())));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ragStreamUrl()))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() >= 300) {
                    throw new BusinessException(ErrorCode.AI_ERROR, "RAG 流式问答服务不可用: HTTP " + response.statusCode());
                }
                return consumeRagEvents(lines, tokenConsumer);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_ERROR, "RAG 流式问答服务不可用: " + ex.getMessage());
        }
    }

    /**
     * 消费 RAG SSE 事件：message 立即转发，citations 作为最终保存依据，error 转成业务异常。
     */
    private List<CitationItem> consumeRagEvents(Stream<String> lines, Consumer<String> tokenConsumer) {
        StringBuilder eventName = new StringBuilder();
        StringBuilder data = new StringBuilder();
        AtomicReference<List<CitationItem>> citations = new AtomicReference<>(List.of());
        lines.forEach(line -> {
            SseEvent event = appendSseLine(line, eventName, data);
            if (event != null) {
                handleRagEvent(event, tokenConsumer, citations);
            }
        });
        SseEvent tailEvent = flushSseEvent(eventName, data);
        if (tailEvent != null) {
            handleRagEvent(tailEvent, tokenConsumer, citations);
        }
        return citations.get();
    }

    /**
     * 按 SSE 协议累积 event/data 行，空行代表一个事件结束。
     */
    private SseEvent appendSseLine(String line, StringBuilder eventName, StringBuilder data) {
        if (line == null || line.isEmpty()) {
            return flushSseEvent(eventName, data);
        }
        if (line.startsWith("event:")) {
            eventName.setLength(0);
            eventName.append(trimSseValue(line.substring("event:".length())));
        } else if (line.startsWith("data:")) {
            if (!data.isEmpty()) {
                data.append('\n');
            }
            data.append(trimSseValue(line.substring("data:".length())));
        }
        return null;
    }

    /**
     * 将当前累积的 SSE 字段转成事件对象，并清空缓冲区。
     */
    private SseEvent flushSseEvent(StringBuilder eventName, StringBuilder data) {
        if (eventName.isEmpty() && data.isEmpty()) {
            return null;
        }
        SseEvent event = new SseEvent(eventName.isEmpty() ? "message" : eventName.toString(), data.toString());
        eventName.setLength(0);
        data.setLength(0);
        return event;
    }

    /**
     * 处理 RAG 返回的单个 SSE 事件。
     */
    private void handleRagEvent(SseEvent event, Consumer<String> tokenConsumer, AtomicReference<List<CitationItem>> citations) {
        switch (event.name()) {
            case "message" -> tokenConsumer.accept(event.data());
            case "citations" -> citations.set(parseCitations(event.data()));
            case "error" -> throw new BusinessException(ErrorCode.AI_ERROR, parseErrorMessage(event.data()));
            default -> {
                // 未知事件暂不透传，避免后续内部诊断事件影响前端协议。
            }
        }
    }

    /**
     * 解析 citations 事件，格式异常时返回空列表，避免引用解析问题影响已完成的文本回答。
     */
    private List<CitationItem> parseCitations(String data) {
        if (data == null || data.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(data,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CitationItem.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 提取 RAG error 事件中的 message 字段，兼容纯文本错误和 JSON 错误。
     */
    private String parseErrorMessage(String data) {
        if (data == null || data.isBlank()) {
            return "RAG 流式问答服务异常";
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            String message = root.path("message").asText();
            return message.isBlank() ? data : message;
        } catch (Exception ignored) {
            return data;
        }
    }

    /**
     * 拼接 RAG 内部流式接口地址，兼容配置末尾是否带斜杠。
     */
    private String ragStreamUrl() {
        String baseUrl = chatProperties.ragStreamBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/internal/rag/ask/stream";
    }

    /**
     * SSE 规范允许冒号后有一个空格；解析时去掉这个协议空格，保留真实内容。
     */
    private String trimSseValue(String value) {
        return value.startsWith(" ") ? value.substring(1) : value;
    }

    /**
     * 查询会话并统一处理不存在的情况。
     */
    private ChatSession mustGet(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw BusinessException.notFound("会话不存在");
        }
        return session;
    }

    /**
     * 保存一条聊天消息。
     */
    private ChatMessage saveMessage(Long sessionId, ChatRole role, String content, String citationsJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 将消息实体转换为前端 VO，并在返回前尽量补齐引用定位信息。
     */
    private ChatMessageVO toMessageVO(ChatMessage entity) {
        return new ChatMessageVO(entity.getId(), entity.getSessionId(), entity.getRole(), entity.getContent(), normalizeCitationsJson(entity.getCitationsJson()), entity.getCreatedAt());
    }

    /**
     * 规范化历史引用 JSON。
     * <p>
     * 旧消息可能只保存了基础 citation 字段，读取历史时尝试调用引用服务补齐页码、章节和段落；
     * 如果 JSON 格式异常则保留原值，避免单条历史消息影响整个会话加载。
     */
    private String normalizeCitationsJson(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return citationsJson;
        }
        try {
            List<CitationItem> citations = objectMapper.readValue(citationsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CitationItem.class));
            return objectMapper.writeValueAsString(citations);
        } catch (Exception ignored) {
            return citationsJson;
        }
    }

    /**
     * 更新会话更新时间，用于会话列表按最近活跃排序。
     */
    private void touchSession(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    /**
     * 将引用来源序列化为 JSON，便于聊天历史回放时展示来源。
     */
    private String toJson(List<CitationItem> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception ex) {
            return "[]";
        }
    }

    /**
     * 向 SSE 客户端发送一个具名事件。
     */
    private void send(SseEmitter emitter, String name, Object data) {
        try {
            Object payload = data instanceof String ? data : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 将流式处理异常转换为 SSE error 事件，业务异常保留原错误码。
     */
    private void sendError(SseEmitter emitter, Exception ex) {
        String code = "500";
        String message = ex.getMessage() == null ? "系统异常，请稍后重试" : ex.getMessage();
        if (ex instanceof BusinessException businessException) {
            code = businessException.getErrorCode().getCode();
        }
        send(emitter, "error", Map.of("code", code, "message", message));
    }

    /**
     * 表示从 RAG 内部 SSE 流中解析出的完整事件。
     */
    private record SseEvent(String name, String data) {
    }
}
