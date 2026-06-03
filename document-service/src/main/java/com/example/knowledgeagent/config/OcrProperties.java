package com.example.knowledgeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * OCR 配置集中定义文档解析阶段的图片识别开关、服务地址和降级阈值。
 */
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {
    private Boolean enabled;
    private String baseUrl;
    private Integer timeoutSeconds;
    private Double minConfidence;
    private Integer pdfMinTextLength;
    private Integer pdfDpi;
    private Docx docx;

    /**
     * Spring Boot 配置绑定需要无参构造器，避免容器启动时无法实例化 OCR 配置对象。
     */
    public OcrProperties() {
    }

    /**
     * 兼容测试和旧构造点，未显式传入 docx 配置时使用默认 DOCX OCR 策略。
     */
    public OcrProperties(Boolean enabled, String baseUrl, Integer timeoutSeconds, Double minConfidence,
                         Integer pdfMinTextLength, Integer pdfDpi) {
        this(enabled, baseUrl, timeoutSeconds, minConfidence, pdfMinTextLength, pdfDpi, null);
    }

    /**
     * 便于单元测试显式构造完整 OCR 配置，生产环境仍通过 application.yml 绑定。
     */
    public OcrProperties(Boolean enabled, String baseUrl, Integer timeoutSeconds, Double minConfidence,
                         Integer pdfMinTextLength, Integer pdfDpi, Docx docx) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.minConfidence = minConfidence;
        this.pdfMinTextLength = pdfMinTextLength;
        this.pdfDpi = pdfDpi;
        this.docx = docx;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(Double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public Integer getPdfMinTextLength() {
        return pdfMinTextLength;
    }

    public void setPdfMinTextLength(Integer pdfMinTextLength) {
        this.pdfMinTextLength = pdfMinTextLength;
    }

    public Integer getPdfDpi() {
        return pdfDpi;
    }

    public void setPdfDpi(Integer pdfDpi) {
        this.pdfDpi = pdfDpi;
    }

    public Docx getDocx() {
        return docx;
    }

    public void setDocx(Docx docx) {
        this.docx = docx;
    }

    /**
     * 判断是否启用 OCR；默认关闭，避免没有部署 sidecar 时影响现有文档解析。
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 返回内部 OCR 服务地址，未配置时使用 docker-compose 中的服务名。
     */
    public String resolvedBaseUrl() {
        return StringUtils.hasText(baseUrl) ? baseUrl : "http://ocr-service:8000";
    }

    /**
     * OCR 调用超时时间，默认 30 秒，避免图片识别卡住索引任务。
     */
    public int resolvedTimeoutSeconds() {
        return timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }

    /**
     * 低于该置信度的识别结果会被丢弃，减少噪声进入向量库。
     */
    public double resolvedMinConfidence() {
        return minConfidence == null ? 0.5 : minConfidence;
    }

    /**
     * PDF 单页原生文本少于该长度时才触发整页 OCR，避免正常文本 PDF 重复索引。
     */
    public int resolvedPdfMinTextLength() {
        return pdfMinTextLength == null || pdfMinTextLength < 0 ? 20 : pdfMinTextLength;
    }

    /**
     * PDF 渲染成图片的 DPI，兼顾中文识别清晰度和索引耗时。
     */
    public int resolvedPdfDpi() {
        return pdfDpi == null || pdfDpi <= 0 ? 200 : pdfDpi;
    }

    /**
     * DOCX 图片 OCR 的独立开关和保护阈值，用于避免超大文档或无效图片拖慢索引。
     */
    public Docx resolvedDocx() {
        return docx == null ? new Docx(null, null, null, null) : docx;
    }

    /**
     * DOCX 内嵌图片识别配置。
     */
    public static class Docx {
        private Boolean enabled;
        private Integer maxImages;
        private Integer minBytes;
        private Long maxPixels;

        /**
         * 嵌套 DOCX 配置同样使用 JavaBean 绑定，保证 application.yml 的 ocr.docx.* 能稳定注入。
         */
        public Docx() {
        }

        public Docx(Boolean enabled, Integer maxImages, Integer minBytes, Long maxPixels) {
            this.enabled = enabled;
            this.maxImages = maxImages;
            this.minBytes = minBytes;
            this.maxPixels = maxPixels;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getMaxImages() {
            return maxImages;
        }

        public void setMaxImages(Integer maxImages) {
            this.maxImages = maxImages;
        }

        public Integer getMinBytes() {
            return minBytes;
        }

        public void setMinBytes(Integer minBytes) {
            this.minBytes = minBytes;
        }

        public Long getMaxPixels() {
            return maxPixels;
        }

        public void setMaxPixels(Long maxPixels) {
            this.maxPixels = maxPixels;
        }

        /**
         * DOCX OCR 继承全局 OCR 开关，单独配置为 false 时可只关闭 DOCX 图片识别。
         */
        public boolean isEnabled(boolean globalEnabled) {
            return globalEnabled && !Boolean.FALSE.equals(enabled);
        }

        /**
         * 单个 DOCX 最多尝试 OCR 的图片数量，避免大量截图导致索引任务长期停留在解析阶段。
         */
        public int resolvedMaxImages() {
            return maxImages == null || maxImages <= 0 ? 30 : maxImages;
        }

        /**
         * 过滤极小图片，例如图标、项目符号和装饰性碎图。
         */
        public int resolvedMinBytes() {
            return minBytes == null || minBytes < 0 ? 512 : minBytes;
        }

        /**
         * 图片最大像素数，超过后跳过 OCR，避免 sidecar 处理超大图片导致内存压力。
         */
        public long resolvedMaxPixels() {
            return maxPixels == null || maxPixels <= 0 ? 12_000_000L : maxPixels;
        }
    }
}
