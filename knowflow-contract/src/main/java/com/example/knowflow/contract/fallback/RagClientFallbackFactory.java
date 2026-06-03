package com.example.knowflow.contract.fallback;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowflow.contract.client.RagClient;
import com.example.knowflow.contract.dto.RagAnswerResult;
import com.example.knowflow.contract.dto.RagAskCommand;
import com.example.knowflow.contract.dto.RagSearchCommand;
import com.example.knowflow.contract.dto.RagSearchResult;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * RAG Feign 降级工厂，供聊天和 Agent 服务在 RAG 不可用时快速失败。
 */
@Component
public class RagClientFallbackFactory implements FallbackFactory<RagClient> {

    /**
     * 根据异常原因创建降级客户端。
     */
    @Override
    public RagClient create(Throwable cause) {
        return new RagClient() {
            @Override
            public ApiResult<RagSearchResult> search(RagSearchCommand request) {
                return ApiResult.fail(ErrorCode.AI_ERROR, "RAG 检索服务不可用: " + cause.getMessage());
            }

            @Override
            public ApiResult<RagAnswerResult> ask(RagAskCommand request) {
                return ApiResult.fail(ErrorCode.AI_ERROR, "RAG 问答服务不可用: " + cause.getMessage());
            }
        };
    }
}
