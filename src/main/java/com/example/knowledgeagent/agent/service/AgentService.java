package com.example.knowledgeagent.agent.service;

import com.example.knowledgeagent.agent.dto.AgentChatRequest;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;

import java.util.function.Consumer;

/**
 * 定义 AgentService 接口，约定该模块对外提供的能力。
 */
public interface AgentService {
    /**
     * 执行 Agent 非流式问答。
     */
    RagAnswerVO chat(AgentChatRequest request);

    /**
     * 执行 Agent 流式问答。
     */
    RagStreamResult chatStream(AgentChatRequest request, Consumer<String> tokenConsumer);
}
