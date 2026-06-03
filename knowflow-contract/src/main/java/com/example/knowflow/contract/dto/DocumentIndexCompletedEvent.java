package com.example.knowflow.contract.dto;

/**
 * 文档索引完成事件，用于通知其他服务刷新统计或展示索引结果。
 */
public record DocumentIndexCompletedEvent(
        Long jobId,
        Long documentId,
        String status,
        Integer chunkCount,
        String errorMessage
) {
}
