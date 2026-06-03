package com.example.knowledgeagent.common.api;

import java.util.List;

/**
 * 定义 PageResult 数据结构，用于在层间传递结构化数据。
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    /**
     * 构造分页响应对象。
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
