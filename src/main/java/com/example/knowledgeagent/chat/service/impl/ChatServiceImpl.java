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
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.service.RagCitationService;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.CitationVO;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
/**
 * 定义 ChatServiceImpl 组件，承载对应模块的业务职责。
 */
public class ChatServiceImpl implements ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemoryService chatMemoryService;
    private final RagService ragService;
    private final RagCitationService citationService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

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
        RagAnswerVO answer = ragService.ask(new RagAskRequest(session.getKnowledgeBaseId(), request.content(), sessionId),
                chatMemoryService.recentHistory(sessionId, ragProperties.maxHistoryMessages()));
        ChatMessage assistantMessage = saveMessage(sessionId, ChatRole.ASSISTANT, answer.answer(), toJson(answer.citations()));
        touchSession(session);
        System.out.println("当前为非流式回答");
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
                RagStreamResult streamResult = ragService.askStream(new RagAskRequest(session.getKnowledgeBaseId(), request.content(), sessionId),
                        chatMemoryService.recentHistory(sessionId, ragProperties.maxHistoryMessages()), token -> {
                            answer.append(token);
                            // 每个 token/片段独立发送，前端按顺序拼接成完整回答。
                            send(emitter, "message", token);
                        });
                // 模型输出结束后再落库助手消息，保证数据库中的 assistant 内容是完整答案。
                ChatMessage assistant = saveMessage(sessionId, ChatRole.ASSISTANT, answer.toString(), toJson(streamResult.citations()));
                touchSession(session);
                send(emitter, "assistantMessageId", assistant.getId());
                send(emitter, "citations", streamResult.citations());
                emitter.complete();
            } catch (Exception ex) {
                sendError(emitter, ex);
                emitter.complete();
            }
        });
        System.out.println("当前为流式返回");
        return emitter;
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
            List<CitationVO> citations = objectMapper.readValue(citationsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CitationVO.class));
            return objectMapper.writeValueAsString(citationService.enrich(citations));
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
    private String toJson(List<CitationVO> citations) {
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
}
