package com.example.knowledgeagent.storage;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/**
 * MinIO 对象存储实现。
 *
 * <p>Docker 环境默认使用该实现接管文档上传，数据库 file_path 保存为
 * minio://bucket/objectKey，避免继续持久化宿主机或容器本地路径。</p>
 */
@Service
@Primary
@Slf4j
public class MinioFileStorageService implements FileStorageService {
    private static final String SCHEME = "minio";

    private final MinioStorageProperties properties;
    private final MinioClient minioClient;

    public MinioFileStorageService(MinioStorageProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(normalizedEndpoint(properties))
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * 将上传文件写入 MinIO bucket。
     *
     * <p>对象 key 沿用原本的业务分层：knowledgeBaseId/日期/UUID.ext，便于人工排查和后续生命周期管理。</p>
     */
    @Override
    public StoredFile store(MultipartFile file, Long knowledgeBaseId) {
        ensureBucket();
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String suffix = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String fileName = UUID.randomUUID() + suffix.toLowerCase();
        String objectKey = String.join("/", String.valueOf(knowledgeBaseId), LocalDate.now().toString(), fileName);
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream")
                    .build());
            return new StoredFile(fileName, originalName, toMinioUri(properties.bucket(), objectKey), file.getSize());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "保存文件到 MinIO 失败: " + ex.getMessage());
        }
    }

    /**
     * 根据 minio://bucket/objectKey 删除对象；删除失败只记录日志，避免影响文档软删除和索引清理。
     */
    @Override
    public void delete(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        MinioObjectRef objectRef = parseMinioUri(filePath);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(objectRef.bucket())
                    .object(objectRef.objectKey())
                    .build());
        } catch (Exception ex) {
            log.warn("删除 MinIO 文件失败，filePath={}", filePath, ex);
        }
    }

    /**
     * 打开 MinIO 对象流，交给 Spring MVC 直接写回响应。
     */
    @Override
    public Resource loadAsResource(String filePath) {
        MinioObjectRef objectRef = parseMinioUri(filePath);
        try {
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(objectRef.bucket())
                    .object(objectRef.objectKey())
                    .build());
            return new InputStreamResource(inputStream);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "读取 MinIO 文件失败: " + ex.getMessage());
        }
    }

    /**
     * 将 MinIO 对象下载为临时文件，供 PDF/DOCX/TXT/MD 解析器复用现有 Path 解析逻辑。
     */
    @Override
    public StoredFileMaterialization materialize(String filePath, String originalFileName) {
        MinioObjectRef objectRef = parseMinioUri(filePath);
        String suffix = extensionOf(originalFileName);
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(objectRef.bucket())
                .object(objectRef.objectKey())
                .build())) {
            Path tempFile = Files.createTempFile("knowflow-minio-", suffix);
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new StoredFileMaterialization(tempFile, true);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "物化 MinIO 文件失败: " + ex.getMessage());
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_ERROR, "初始化 MinIO bucket 失败: " + ex.getMessage());
        }
    }

    private String normalizedEndpoint(MinioStorageProperties properties) {
        String endpoint = properties.endpoint();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return (properties.secure() ? "https://" : "http://") + endpoint;
    }

    private String toMinioUri(String bucket, String objectKey) {
        return SCHEME + "://" + bucket + "/" + objectKey;
    }

    private MinioObjectRef parseMinioUri(String filePath) {
        try {
            URI uri = URI.create(filePath);
            if (!SCHEME.equals(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw BusinessException.badRequest("无效的 MinIO 文件路径: " + filePath);
            }
            String objectKey = uri.getPath();
            if (objectKey.startsWith("/")) {
                objectKey = objectKey.substring(1);
            }
            if (!StringUtils.hasText(objectKey)) {
                throw BusinessException.badRequest("MinIO 文件路径缺少对象 key: " + filePath);
            }
            return new MinioObjectRef(uri.getHost(), objectKey);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("无效的 MinIO 文件路径: " + filePath);
        }
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return ".tmp";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".tmp";
        }
        return fileName.substring(dotIndex).toLowerCase();
    }

    private record MinioObjectRef(String bucket, String objectKey) {
    }
}
