package com.example.knowledgeagent.document.ocr;

import com.example.knowledgeagent.config.OcrProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 通过内部 HTTP sidecar 调用本地 OCR 引擎，document-service 本身不绑定 Python/模型运行时。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpOcrService implements OcrService {
    private final OcrProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * OCR 默认关闭，只有显式配置 enabled=true 后才会在解析阶段调用 sidecar。
     */
    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 以 multipart/form-data 上传图片到 sidecar；任何异常都降级为失败结果，避免阻断文档索引。
     */
    @Override
    public OcrResult recognize(byte[] imageBytes, String fileName) {
        long startAt = System.currentTimeMillis();
        if (!isEnabled()) {
            log.debug("OCR 未启用，跳过图片识别，fileName={}", fileName);
            return OcrResult.failure("OCR disabled", 0);
        }
        if (imageBytes == null || imageBytes.length == 0) {
            log.debug("OCR 跳过空图片，fileName={}", fileName);
            return OcrResult.failure("empty image", 0);
        }
        try {
            String boundary = "KnowflowOcrBoundary" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.resolvedBaseUrl().replaceAll("/+$", "") + "/ocr"))
                    .timeout(Duration.ofSeconds(properties.resolvedTimeoutSeconds()))
                    // Uvicorn 不支持 Java HttpClient 默认的 h2c 明文升级；OCR sidecar 固定走 HTTP/1.1，避免 multipart 请求被 422 拒收。
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(multipartBody(boundary, fileName, imageBytes)))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.resolvedTimeoutSeconds()))
                    // 与 request.version 保持一致，避免 JDK 在容器内对 HTTP/2 做额外升级探测。
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = System.currentTimeMillis() - startAt;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OCR 服务返回异常状态，status={}, fileName={}, bytes={}, elapsedMs={}, body={}",
                        response.statusCode(), fileName, imageBytes.length, elapsedMs, abbreviate(response.body()));
                return OcrResult.failure("HTTP " + response.statusCode(), elapsedMs);
            }
            OcrResult result = parseResponse(response.body(), elapsedMs);
            if (result.success()) {
                log.info("OCR 识别成功，fileName={}, bytes={}, confidence={}, elapsedMs={}, textLength={}",
                        fileName, imageBytes.length, result.confidence(), result.elapsedMs(), result.text().length());
            } else {
                log.warn("OCR 识别降级，fileName={}, bytes={}, elapsedMs={}, reason={}",
                        fileName, imageBytes.length, result.elapsedMs(), result.error());
            }
            return result;
        } catch (Exception ex) {
            long elapsedMs = System.currentTimeMillis() - startAt;
            log.warn("OCR 调用失败，fileName={}, bytes={}, elapsedMs={}, message={}",
                    fileName, imageBytes.length, elapsedMs, ex.getMessage());
            return OcrResult.failure(ex.getMessage(), elapsedMs);
        }
    }

    /**
     * 解析 sidecar 返回值，同时兼容 text/confidence 与 avgConfidence 两种字段命名。
     */
    private OcrResult parseResponse(String body, long elapsedMs) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        String text = root.path("text").asText("");
        double confidence = root.has("confidence") ? root.path("confidence").asDouble(0.0)
                : root.path("avgConfidence").asDouble(0.0);
        if (!root.path("success").asBoolean(true)) {
            return OcrResult.failure(root.path("error").asText("OCR failed"), elapsedMs);
        }
        if (!StringUtils.hasText(text)) {
            return OcrResult.failure("empty OCR text", elapsedMs);
        }
        if (confidence < properties.resolvedMinConfidence()) {
            return OcrResult.failure("low confidence: " + confidence, elapsedMs);
        }
        return OcrResult.success(text.trim(), confidence, elapsedMs);
    }

    /**
     * 手工构造 multipart body，避免为单个内部调用额外引入 HTTP 客户端依赖。
     */
    private List<byte[]> multipartBody(String boundary, String fileName, byte[] imageBytes) {
        String safeFileName = StringUtils.hasText(fileName) ? fileName : "image.png";
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        return List.of(head.getBytes(StandardCharsets.UTF_8), imageBytes, tail.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 只截断日志中的异常响应体，避免 OCR sidecar 返回大段错误信息时刷爆索引日志。
     */
    private String abbreviate(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
