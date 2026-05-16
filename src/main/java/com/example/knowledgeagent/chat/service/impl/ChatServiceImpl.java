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
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.CitationVO;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemoryService chatMemoryService;
    private final RagService ragService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

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

    @Override
    public PageResult<ChatSessionVO> listSessions(Long knowledgeBaseId, long page, long size) {
        Page<ChatSession> result = chatSessionMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ChatSession>()
                .eq(knowledgeBaseId != null, ChatSession::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(ChatSession::getUpdatedAt));
        return PageResult.of(result.getRecords().stream().map(ChatSessionVO::from).toList(), result.getTotal(), page, size);
    }

    @Override
    public ChatSessionVO getSession(Long sessionId) {
        return ChatSessionVO.from(mustGet(sessionId));
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        mustGet(sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        chatSessionMapper.deleteById(sessionId);
    }

    @Override
    public PageResult<ChatMessageVO> getMessages(Long sessionId, long page, long size) {
        mustGet(sessionId);
        Page<ChatMessage> result = chatMessageMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt));
        return PageResult.of(result.getRecords().stream().map(ChatMessageVO::from).toList(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public ChatReplyVO sendMessage(Long sessionId, SendMessageRequest request) {
        ChatSession session = mustGet(sessionId);
        ChatMessage userMessage = saveMessage(sessionId, ChatRole.USER, request.content(), null);
        RagAnswerVO answer = ragService.ask(new RagAskRequest(session.getKnowledgeBaseId(), request.content(), sessionId),
                chatMemoryService.recentHistory(sessionId, ragProperties.maxHistoryMessages()));
        ChatMessage assistantMessage = saveMessage(sessionId, ChatRole.ASSISTANT, answer.answer(), toJson(answer.citations()));
        touchSession(session);
        return new ChatReplyVO(userMessage.getId(), assistantMessage.getId(), answer.answer(), answer.citations());
    }

    @Override
    public SseEmitter sendMessageStream(Long sessionId, SendMessageRequest request) {
        ChatSession session = mustGet(sessionId);
        ChatMessage userMessage = saveMessage(sessionId, ChatRole.USER, request.content(), null);
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            try {
                emitter.send(SseEmitter.event().name("userMessageId").data(userMessage.getId(), MediaType.APPLICATION_JSON));
                RagStreamResult streamResult = ragService.askStream(new RagAskRequest(session.getKnowledgeBaseId(), request.content(), sessionId),
                        chatMemoryService.recentHistory(sessionId, ragProperties.maxHistoryMessages()), token -> {
                            answer.append(token);
                            send(emitter, "message", token);
                        });
                ChatMessage assistant = saveMessage(sessionId, ChatRole.ASSISTANT, answer.toString(), toJson(streamResult.citations()));
                touchSession(session);
                send(emitter, "assistantMessageId", assistant.getId());
                send(emitter, "citations", streamResult.citations());
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private ChatSession mustGet(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw BusinessException.notFound("会话不存在");
        }
        return session;
    }

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

    private void touchSession(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    private String toJson(List<CitationVO> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
