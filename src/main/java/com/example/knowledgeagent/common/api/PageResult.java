package com.example.knowledgeagent.common.api;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size) {

    /**
     * 构造分页响应对象。
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
