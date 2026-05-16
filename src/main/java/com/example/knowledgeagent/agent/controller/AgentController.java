package com.example.knowledgeagent.agent.controller;

import com.example.knowledgeagent.agent.dto.AgentChatRequest;
import com.example.knowledgeagent.agent.service.AgentService;
import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    @PostMapping("/chat")
    public ApiResult<RagAnswerVO> chat(@Valid @RequestBody AgentChatRequest request) {
        return ApiResult.ok(agentService.chat(request));
    }
}
