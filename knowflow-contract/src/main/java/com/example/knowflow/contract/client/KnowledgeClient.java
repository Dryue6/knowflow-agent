package com.example.knowflow.contract.client;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowflow.contract.dto.KnowledgeBaseInfo;
import com.example.knowflow.contract.dto.KnowledgeStatisticsUpdateRequest;
import com.example.knowflow.contract.fallback.KnowledgeClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 知识库服务内部调用契约，供文档、RAG 等服务校验知识库和刷新统计。
 */
@FeignClient(name = "knowledge-service", contextId = "knowledgeClient", fallbackFactory = KnowledgeClientFallbackFactory.class)
public interface KnowledgeClient {

    /**
     * 查询知识库详情，用于服务间校验知识库是否存在。
     */
    @GetMapping("/internal/knowledge-bases/{id}")
    ApiResult<KnowledgeBaseInfo> getKnowledgeBase(@PathVariable("id") Long id);

    /**
     * 更新知识库统计，通常由文档服务在上传、删除、索引完成后触发。
     */
    @PostMapping("/internal/knowledge-bases/statistics")
    ApiResult<Void> updateStatistics(@RequestBody KnowledgeStatisticsUpdateRequest request);
}
