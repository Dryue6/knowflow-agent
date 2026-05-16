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
    /**
     * 执行向量检索并返回相似切片。
     */
    RagSearchResponseVO retrieve(RagSearchRequest request);

    /**
     * 执行非流式 RAG 问答。
     */
    RagAnswerVO ask(RagAskRequest request, List<ChatHistoryMessage> history);

    /**
     * 执行流式 RAG 问答，通过回调输出增量文本。
     */
    RagStreamResult askStream(RagAskRequest request, List<ChatHistoryMessage> history, Consumer<String> tokenConsumer);
}
