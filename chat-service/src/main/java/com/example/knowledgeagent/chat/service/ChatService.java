package com.example.knowledgeagent.chat.service;

import com.example.knowledgeagent.chat.dto.CreateChatSessionRequest;
import com.example.knowledgeagent.chat.dto.SendMessageRequest;
import com.example.knowledgeagent.chat.vo.ChatMessageVO;
import com.example.knowledgeagent.chat.vo.ChatReplyVO;
import com.example.knowledgeagent.chat.vo.ChatSessionVO;
import com.example.knowledgeagent.common.api.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 定义 ChatService 接口，约定该模块对外提供的能力。
 */
public interface ChatService {
    /**
     * 创建聊天会话。
     */
    ChatSessionVO createSession(CreateChatSessionRequest request);

    /**
     * 分页查询聊天会话。
     */
    PageResult<ChatSessionVO> listSessions(Long knowledgeBaseId, long page, long size);

    /**
     * 查询会话详情。
     */
    ChatSessionVO getSession(Long sessionId);

    /**
     * 删除会话。
     */
    void deleteSession(Long sessionId);

    /**
     * 分页查询会话消息。
     */
    PageResult<ChatMessageVO> getMessages(Long sessionId, long page, long size);

    /**
     * 发送非流式消息。
     */
    ChatReplyVO sendMessage(Long sessionId, SendMessageRequest request);

    /**
     * 发送流式消息并返回 SSE emitter。
     */
    SseEmitter sendMessageStream(Long sessionId, SendMessageRequest request);
}
