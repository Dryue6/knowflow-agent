package com.example.knowledgeagent.storage;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.MinioStorageProperties;
import com.example.knowledgeagent.document.dto.PresignedUploadUrlResponse;
import com.example.knowledgeagent.document.enums.FileType;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO Presigned URL 直传服务。
 *
 * <p>该服务只负责签名、对象 key 约束、对象存在性和大小校验；创建文档与索引任务仍由文档业务服务完成。</p>
 */
@Service
@Slf4j
public class MinioPresignedUploadService {
    private static final int UPLOAD_URL_EXPIRE_MINUTES = 10;
    private static final String MINIO_SCHEME = "minio";
    private static final String DEFAULT_REGION = "us-east-1";

    private final MinioStorageProperties properties;
    private final MinioClient internalClient;
    private final MinioClient publicClient;

    public MinioPresignedUploadService(MinioStorageProperties properties) {
        this.properties = properties;
        this.internalClient = MinioClient.builder()
                .endpoint(normalizedEndpoint(properties.endpoint(), properties.secure()))
                .credentials(properties.accessKey(), properties.secretKey())
                .region(DEFAULT_REGION)
                .build();
        this.publicClient = MinioClient.builder()
                .endpoint(normalizedEndpoint(properties.publicEndpoint(), properties.secure()))
                .credentials(properties.accessKey(), properties.secretKey())
                .region(DEFAULT_REGION)
                .build();
    }

    /**
     * 为前端直传生成短期 PUT URL。
     *
     * <p>对象 key 固定绑定知识库前缀，完成确认时也会校验该前缀，防止客户端伪造其他知识库对象。</p>
     */
    public PresignedUploadUrlResponse createUploadUrl(Long knowledgeBaseId, String originalFileName) {
        FileType.fromFileName(originalFileName);
        String cleanName = StringUtils.cleanPath(originalFileName == null ? "document" : originalFileName);
        String suffix = cleanName.contains(".") ? cleanName.substring(cleanName.lastIndexOf('.')).toLowerCase() : "";
        String fileName = UUID.randomUUID() + suffix;
        String objectKey = String.join("/", String.valueOf(knowledgeBaseId), LocalDate.now().toString(), fileName);
        Instant expiresAt = Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(UPLOAD_URL_EXPIRE_MINUTES));
        try {
            String uploadUrl = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(UPLOAD_URL_EXPIRE_MINUTES, TimeUnit.MINUTES)
                    .build());
            return new PresignedUploadUrlResponse(uploadUrl, objectKey, properties.bucket(), toMinioUri(objectKey), expiresAt);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "生成 MinIO 直传地址失败: " + ex.getMessage());
        }
    }

    /**
     * 校验前端直传完成后的对象，并转换为文档服务可持久化的 StoredFile。
     */
    public StoredFile confirmUploadedObject(Long knowledgeBaseId, String objectKey, String originalFileName, long expectedSize) {
        validateObjectKey(knowledgeBaseId, objectKey);
        FileType.fromFileName(originalFileName);
        try {
            StatObjectResponse stat = internalClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
            if (stat.size() != expectedSize) {
                deleteObjectQuietly(objectKey);
                throw BusinessException.badRequest("MinIO 对象大小与上传声明不一致");
            }
            String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
            return new StoredFile(fileName, StringUtils.cleanPath(originalFileName), toMinioUri(objectKey), stat.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "校验 MinIO 上传对象失败: " + ex.getMessage());
        }
    }

    /**
     * 静默删除直传对象，用于完成确认失败后的补偿清理。
     */
    public void deleteObjectQuietly(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            internalClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.warn("清理 MinIO 直传对象失败，objectKey={}", objectKey, ex);
        }
    }

    private void validateObjectKey(Long knowledgeBaseId, String objectKey) {
        String requiredPrefix = knowledgeBaseId + "/";
        if (!StringUtils.hasText(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")
                || !objectKey.startsWith(requiredPrefix)) {
            throw BusinessException.badRequest("无效的 MinIO 对象 key");
        }
    }

    private String toMinioUri(String objectKey) {
        return MINIO_SCHEME + "://" + properties.bucket() + "/" + objectKey;
    }

    private String normalizedEndpoint(String endpoint, boolean secure) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return (secure ? "https://" : "http://") + endpoint;
    }
}
