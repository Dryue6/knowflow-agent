package com.example.knowledgeagent.chat.service;

import com.example.knowledgeagent.chat.dto.CreateChatSessionRequest;
import com.example.knowledgeagent.chat.dto.SendMessageRequest;
import com.example.knowledgeagent.chat.vo.ChatMessageVO;
import com.example.knowledgeagent.chat.vo.ChatReplyVO;
import com.example.knowledgeagent.chat.vo.ChatSessionVO;
import com.example.knowledgeagent.common.api.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    ChatSessionVO createSession(CreateChatSessionRequest request);

    PageResult<ChatSessionVO> listSessions(Long knowledgeBaseId, long page, long size);

    ChatSessionVO getSession(Long sessionId);

    void deleteSession(Long sessionId);

    PageResult<ChatMessageVO> getMessages(Long sessionId, long page, long size);

    ChatReplyVO sendMessage(Long sessionId, SendMessageRequest request);

    SseEmitter sendMessageStream(Long sessionId, SendMessageRequest request);
}
