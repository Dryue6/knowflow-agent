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
/**
 * 定义 EnterpriseKnowledgeAgentService 组件，承载对应模块的业务职责。
 */
public class EnterpriseKnowledgeAgentService implements AgentService {
    private final RagService ragService;

    /**
     * Agent 非流式问答入口。
     * <p>
     * 第一阶段 Agent 作为 RAG 门面存在，后续可以在这里加入意图判断、工具选择和多步骤执行。
     */
    @Override
    public RagAnswerVO chat(AgentChatRequest request) {
        return ragService.ask(new RagAskRequest(request.knowledgeBaseId(), request.question(), request.sessionId()), List.of());
    }

    /**
     * Agent 流式问答入口。
     */
    @Override
    public RagStreamResult chatStream(AgentChatRequest request, Consumer<String> tokenConsumer) {
        return ragService.askStream(new RagAskRequest(request.knowledgeBaseId(), request.question(), request.sessionId()), List.of(), tokenConsumer);
    }
}
