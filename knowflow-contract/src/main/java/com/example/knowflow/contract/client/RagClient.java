package com.example.knowflow.contract.client;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowflow.contract.dto.RagAnswerResult;
import com.example.knowflow.contract.dto.RagAskCommand;
import com.example.knowflow.contract.dto.RagSearchCommand;
import com.example.knowflow.contract.dto.RagSearchResult;
import com.example.knowflow.contract.fallback.RagClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * RAG 服务内部调用契约，供聊天服务和 Agent 服务复用检索问答能力。
 */
@FeignClient(name = "rag-service", contextId = "ragClient", fallbackFactory = RagClientFallbackFactory.class)
public interface RagClient {

    /**
     * 调用 RAG 检索接口。
     */
    @PostMapping("/internal/rag/search")
    ApiResult<RagSearchResult> search(@RequestBody RagSearchCommand request);

    /**
     * 调用 RAG 非流式问答接口。
     */
    @PostMapping("/internal/rag/ask")
    ApiResult<RagAnswerResult> ask(@RequestBody RagAskCommand request);
}
