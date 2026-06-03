package com.example.knowflow.contract.fallback;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowflow.contract.client.DocumentClient;
import com.example.knowflow.contract.dto.DocumentAdjacentContextCommand;
import com.example.knowflow.contract.dto.DocumentFixedContextCommand;
import com.example.knowflow.contract.dto.DocumentKeywordSearchCommand;
import com.example.knowflow.contract.dto.DocumentVectorSearchCommand;
import com.example.knowflow.contract.dto.RagSearchItem;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档检索 Feign 降级工厂，RAG 在文档服务不可用时可快速失败。
 */
@Component
public class DocumentClientFallbackFactory implements FallbackFactory<DocumentClient> {

    /**
     * 根据异常原因创建降级客户端。
     */
    @Override
    public DocumentClient create(Throwable cause) {
        return new DocumentClient() {
            @Override
            public ApiResult<List<RagSearchItem>> vectorSearch(DocumentVectorSearchCommand request) {
                return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "文档向量召回不可用: " + cause.getMessage());
            }

            @Override
            public ApiResult<List<RagSearchItem>> keywordSearch(DocumentKeywordSearchCommand request) {
                return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "文档关键词召回不可用: " + cause.getMessage());
            }

            @Override
            public ApiResult<List<RagSearchItem>> fixedContext(DocumentFixedContextCommand request) {
                return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "文档固定上下文不可用: " + cause.getMessage());
            }

            @Override
            public ApiResult<List<RagSearchItem>> adjacentContext(DocumentAdjacentContextCommand request) {
                return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "文档相邻上下文不可用: " + cause.getMessage());
            }
        };
    }
}
