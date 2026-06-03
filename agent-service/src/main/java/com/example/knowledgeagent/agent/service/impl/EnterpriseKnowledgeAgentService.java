package com.example.knowledgeagent.agent.service.impl;

import com.example.knowledgeagent.agent.dto.AgentChatRequest;
import com.example.knowledgeagent.agent.service.AgentService;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowflow.contract.client.RagClient;
import com.example.knowflow.contract.dto.RagAnswerResult;
import com.example.knowflow.contract.dto.RagAskCommand;
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
    private final RagClient ragClient;

    /**
     * Agent 非流式问答入口。
     * <p>
     * 第一阶段 Agent 作为 RAG 门面存在，后续可以在这里加入意图判断、工具选择和多步骤执行。
     */
    @Override
    public RagAnswerResult chat(AgentChatRequest request) {
        var response = ragClient.ask(new RagAskCommand(request.knowledgeBaseId(), request.question(), request.sessionId(), List.of()));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            throw new BusinessException(ErrorCode.AI_ERROR, response.message());
        }
        return response.data();
    }

    /**
     * Agent 流式问答入口。
     */
    @Override
    public RagAnswerResult chatStream(AgentChatRequest request, Consumer<String> tokenConsumer) {
        RagAnswerResult result = chat(request);
        tokenConsumer.accept(result.answer());
        return result;
    }
}
