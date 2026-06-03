package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "vector")
/**
 * 定义向量库类型配置；默认使用 pgvector，保证容器重启后仍能检索数据库中的历史向量。
 */
public record VectorStoreProperties(String type) {
    public VectorStoreProperties {
        // 缺省使用持久化向量库，避免微服务拆分后因缺少配置退回空的内存向量库。
        type = StringUtils.hasText(type) ? type : "pgvector";
    }
}
