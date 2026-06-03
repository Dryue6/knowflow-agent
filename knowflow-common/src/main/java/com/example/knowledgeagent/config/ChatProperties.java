package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 定义 chat-service 调用下游服务所需的配置。
 */
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(String ragStreamBaseUrl) {

    /**
     * 为 Docker 微服务环境提供默认 RAG 流式地址，避免 Nacos 配置缺失时回退到不可达地址。
     */
    public ChatProperties {
        ragStreamBaseUrl = StringUtils.hasText(ragStreamBaseUrl) ? ragStreamBaseUrl : "http://rag-service:18084";
    }
}
