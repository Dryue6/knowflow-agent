package com.example.knowledgeagent.document.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 申请 MinIO 直传地址的请求参数。
 *
 * <p>后端会基于文件名识别业务文件类型，并用文件大小做基础边界校验，避免为无效上传签发 URL。</p>
 */
public record CreatePresignedUploadUrlRequest(
        @NotBlank String originalFileName,
        @NotNull @Min(1) Long fileSize,
        String contentType
) {
}
