package com.example.knowledgeagent.chat.controller;

import com.example.knowledgeagent.chat.dto.CreateChatSessionRequest;
import com.example.knowledgeagent.chat.dto.SendMessageRequest;
import com.example.knowledgeagent.chat.service.ChatService;
import com.example.knowledgeagent.chat.vo.ChatMessageVO;
import com.example.knowledgeagent.chat.vo.ChatReplyVO;
import com.example.knowledgeagent.chat.vo.ChatSessionVO;
import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/sessions")
/**
 * 定义 ChatController 组件，承载对应模块的业务职责。
 */
public class ChatController {
    private final ChatService chatService;

    /**
     * 创建一个绑定知识库的聊天会话。
     */
    @PostMapping
    public ApiResult<ChatSessionVO> create(@Valid @RequestBody CreateChatSessionRequest request) {
        return ApiResult.ok(chatService.createSession(request));
    }

    /**
     * 分页查询聊天会话，可按知识库过滤。
     */
    @GetMapping
    public ApiResult<PageResult<ChatSessionVO>> page(@RequestParam(required = false) Long knowledgeBaseId,
                                                     @RequestParam(defaultValue = "1") @Min(1) long page,
                                                     @RequestParam(defaultValue = "10") @Min(1) long size) {
        return ApiResult.ok(chatService.listSessions(knowledgeBaseId, page, size));
    }

    /**
     * 查询会话详情。
     */
    @GetMapping("/{sessionId}")
    public ApiResult<ChatSessionVO> detail(@PathVariable Long sessionId) {
        return ApiResult.ok(chatService.getSession(sessionId));
    }

    /**
     * 删除会话及消息记录。
     */
    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> delete(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResult.ok();
    }

    /**
     * 分页查询会话消息。
     */
    @GetMapping("/{sessionId}/messages")
    public ApiResult<PageResult<ChatMessageVO>> messages(@PathVariable Long sessionId,
                                                         @RequestParam(defaultValue = "1") @Min(1) long page,
                                                         @RequestParam(defaultValue = "20") @Min(1) long size) {
        return ApiResult.ok(chatService.getMessages(sessionId, page, size));
    }

    /**
     * 发送非流式消息并返回完整 AI 答案。
     */
    @PostMapping("/{sessionId}/messages")
    public ApiResult<ChatReplyVO> send(@PathVariable Long sessionId, @Valid @RequestBody SendMessageRequest request) {
        return ApiResult.ok(chatService.sendMessage(sessionId, request));
    }

    /**
     * 发送流式消息，使用 text/event-stream 返回增量答案。
     */
    @PostMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long sessionId, @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessageStream(sessionId, request);
    }
}
