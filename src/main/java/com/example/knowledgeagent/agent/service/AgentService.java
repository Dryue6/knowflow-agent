package com.example.knowledgeagent.agent.service;

import com.example.knowledgeagent.agent.dto.AgentChatRequest;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;

import java.util.function.Consumer;

public interface AgentService {
    RagAnswerVO chat(AgentChatRequest request);

    RagStreamResult chatStream(AgentChatRequest request, Consumer<String> tokenConsumer);
}
