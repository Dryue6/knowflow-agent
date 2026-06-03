package com.example.knowledgeagent.document.dto;

import java.time.Instant;

/**
 * MinIO 直传地址响应。
 *
 * <p>uploadUrl 面向浏览器访问，filePath 是后端完成确认后会写入数据库的稳定对象引用。</p>
 */
public record PresignedUploadUrlResponse(
        String uploadUrl,
        String objectKey,
        String bucket,
        String filePath,
        Instant expiresAt
) {
}
