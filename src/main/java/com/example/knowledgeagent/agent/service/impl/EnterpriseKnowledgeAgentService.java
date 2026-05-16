package com.example.knowledgeagent.agent.service.impl;

import com.example.knowledgeagent.agent.dto.AgentChatRequest;
import com.example.knowledgeagent.agent.service.AgentService;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class EnterpriseKnowledgeAgentService implements AgentService {
    private final RagService ragService;

    @Override
    public RagAnswerVO chat(AgentChatRequest request) {
        return ragService.ask(new RagAskRequest(request.knowledgeBaseId(), request.question(), request.sessionId()), List.of());
    }

    @Override
    public RagStreamResult chatStream(AgentChatRequest request, Consumer<String> tokenConsumer) {
        return ragService.askStream(new RagAskRequest(request.knowledgeBaseId(), request.question(), request.sessionId()), List.of(), tokenConsumer);
    }
}
