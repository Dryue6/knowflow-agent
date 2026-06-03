package com.example.knowledgeagent.document.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * MinIO 直传完成确认请求。
 *
 * <p>前端完成 PUT 后提交对象 key 和文件元信息，后端会重新向 MinIO 校验对象存在性和大小后再创建文档记录。</p>
 */
public record CompletePresignedUploadRequest(
        @NotBlank String objectKey,
        @NotBlank String originalFileName,
        @NotNull @Min(1) Long fileSize,
        String contentType
) {
}
