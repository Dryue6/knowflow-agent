package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 定义本地文件存储配置；当前主要作为兼容配置保留。
 */
@ConfigurationProperties(prefix = "storage.local")
public record FileStorageProperties(String basePath) {
    public FileStorageProperties {
        // Docker 部署时如果仍使用本地存储，文件只能写入容器挂载目录，避免把宿主机绝对路径保存进数据库。
        basePath = StringUtils.hasText(basePath) ? basePath : "/app/data/uploads";
    }
}
