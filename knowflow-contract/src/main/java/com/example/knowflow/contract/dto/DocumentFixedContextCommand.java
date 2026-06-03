package com.example.knowflow.contract.dto;

/**
 * 固定上下文查询请求，用于获取系统约束或置顶资料切片。
 */
public record DocumentFixedContextCommand(
        Long knowledgeBaseId,
        String constraintLevel,
        Integer limit
) {
}
