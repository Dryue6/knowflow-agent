package com.example.knowflow.contract.dto;

import java.util.List;

/**
 * RAG 命中切片后的相邻上下文查询请求。
 *
 * <p>向量召回只负责找最相关切片，本请求用于在回答组装阶段补齐同一文档前后片段，
 * 避免 OCR 或短 chunk 命中后缺少上下文。</p>
 */
public record DocumentAdjacentContextCommand(
        Long knowledgeBaseId,
        List<Anchor> anchors,
        Integer windowSize,
        Integer limit
) {
    /**
     * 定义需要扩展上下文的命中切片位置。
     */
    public record Anchor(Long documentId, Integer chunkIndex, Long chunkId) {
    }
}
