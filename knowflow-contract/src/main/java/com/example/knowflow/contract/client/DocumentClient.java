package com.example.knowflow.contract.client;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowflow.contract.dto.DocumentAdjacentContextCommand;
import com.example.knowflow.contract.dto.DocumentFixedContextCommand;
import com.example.knowflow.contract.dto.DocumentKeywordSearchCommand;
import com.example.knowflow.contract.dto.DocumentVectorSearchCommand;
import com.example.knowflow.contract.dto.RagSearchItem;
import com.example.knowflow.contract.fallback.DocumentClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 文档服务内部检索契约，隔离 rag-service 对 document 表和向量库的直接访问。
 */
@FeignClient(name = "document-service", contextId = "documentClient", fallbackFactory = DocumentClientFallbackFactory.class)
public interface DocumentClient {

    /**
     * 执行向量召回。
     */
    @PostMapping("/internal/documents/search/vector")
    ApiResult<List<RagSearchItem>> vectorSearch(@RequestBody DocumentVectorSearchCommand request);

    /**
     * 执行关键词召回。
     */
    @PostMapping("/internal/documents/search/keyword")
    ApiResult<List<RagSearchItem>> keywordSearch(@RequestBody DocumentKeywordSearchCommand request);

    /**
     * 查询固定上下文切片。
     */
    @PostMapping("/internal/documents/context/fixed")
    ApiResult<List<RagSearchItem>> fixedContext(@RequestBody DocumentFixedContextCommand request);

    /**
     * 查询命中切片的相邻上下文。
     */
    @PostMapping("/internal/documents/context/adjacent")
    ApiResult<List<RagSearchItem>> adjacentContext(@RequestBody DocumentAdjacentContextCommand request);
}
