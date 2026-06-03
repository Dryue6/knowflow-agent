package com.example.knowflow.contract.dto;

/**
 * 文档索引请求事件，用于描述一次异步索引任务的最小业务上下文。
 */
public record DocumentIndexRequestedEvent(
        Long jobId,
        Long documentId,
        Long knowledgeBaseId,
        String jobType
) {
}
