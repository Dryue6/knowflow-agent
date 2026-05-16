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
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ApiResult<ChatSessionVO> create(@Valid @RequestBody CreateChatSessionRequest request) {
        return ApiResult.ok(chatService.createSession(request));
    }

    @GetMapping
    public ApiResult<PageResult<ChatSessionVO>> page(@RequestParam(required = false) Long knowledgeBaseId,
                                                     @RequestParam(defaultValue = "1") @Min(1) long page,
                                                     @RequestParam(defaultValue = "10") @Min(1) long size) {
        return ApiResult.ok(chatService.listSessions(knowledgeBaseId, page, size));
    }

    @GetMapping("/{sessionId}")
    public ApiResult<ChatSessionVO> detail(@PathVariable Long sessionId) {
        return ApiResult.ok(chatService.getSession(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> delete(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResult.ok();
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResult<PageResult<ChatMessageVO>> messages(@PathVariable Long sessionId,
                                                         @RequestParam(defaultValue = "1") @Min(1) long page,
                                                         @RequestParam(defaultValue = "20") @Min(1) long size) {
        return ApiResult.ok(chatService.getMessages(sessionId, page, size));
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResult<ChatReplyVO> send(@PathVariable Long sessionId, @Valid @RequestBody SendMessageRequest request) {
        return ApiResult.ok(chatService.sendMessage(sessionId, request));
    }

    @PostMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long sessionId, @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessageStream(sessionId, request);
    }
}
