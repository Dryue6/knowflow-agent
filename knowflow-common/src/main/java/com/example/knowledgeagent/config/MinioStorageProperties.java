package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * MinIO 对象存储配置。
 *
 * <p>document-service 通过这些配置连接 Docker Compose 内的 MinIO 服务；
 * endpoint 默认使用容器服务名，bucket 默认保存全部上传文档对象。</p>
 */
@ConfigurationProperties(prefix = "storage.minio")
public record MinioStorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        boolean secure
) {
    public MinioStorageProperties {
        endpoint = StringUtils.hasText(endpoint) ? endpoint : "http://minio:9000";
        publicEndpoint = StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint;
        accessKey = StringUtils.hasText(accessKey) ? accessKey : "knowflow";
        secretKey = StringUtils.hasText(secretKey) ? secretKey : "knowflow123";
        bucket = StringUtils.hasText(bucket) ? bucket : "knowflow-documents";
    }
}
