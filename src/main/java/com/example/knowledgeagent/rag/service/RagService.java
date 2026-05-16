package com.example.knowledgeagent.rag.service;

import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagSearchResponseVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;

import java.util.List;
import java.util.function.Consumer;

public interface RagService {
    RagSearchResponseVO retrieve(RagSearchRequest request);

    RagAnswerVO ask(RagAskRequest request, List<ChatHistoryMessage> history);

    RagStreamResult askStream(RagAskRequest request, List<ChatHistoryMessage> history, Consumer<String> tokenConsumer);
}
