package com.example.knowflow.contract.dto;

import java.util.List;

/**
 * 文档服务向量召回请求，由 document-service 负责 embedding 和 pgvector 查询。
 */
public record DocumentVectorSearchCommand(
        Long knowledgeBaseId,
        List<String> queryVariants,
        Integer topK,
        Double minScore
) {
}
