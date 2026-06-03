package com.example.knowflow.contract.dto;

/**
 * 知识库统计更新请求，由文档服务在文档数量或切片数量变化后发送给知识库服务。
 */
public record KnowledgeStatisticsUpdateRequest(
        Long knowledgeBaseId,
        Integer documentCount,
        Integer chunkCount
) {
}
