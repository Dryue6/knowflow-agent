package com.example.knowflow.contract.fallback;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowflow.contract.client.KnowledgeClient;
import com.example.knowflow.contract.dto.KnowledgeBaseInfo;
import com.example.knowflow.contract.dto.KnowledgeStatisticsUpdateRequest;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 知识库 Feign 降级工厂，保证调用失败时返回统一错误结构。
 */
@Component
public class KnowledgeClientFallbackFactory implements FallbackFactory<KnowledgeClient> {

    /**
     * 根据异常原因创建降级客户端。
     */
    @Override
    public KnowledgeClient create(Throwable cause) {
        return new KnowledgeClient() {
            @Override
            public ApiResult<KnowledgeBaseInfo> getKnowledgeBase(Long id) {
                return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "知识库服务不可用: " + cause.getMessage());
            }

            @Override
            public ApiResult<Void> updateStatistics(KnowledgeStatisticsUpdateRequest request) {
                return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "知识库统计更新失败: " + cause.getMessage());
            }
        };
    }
}

