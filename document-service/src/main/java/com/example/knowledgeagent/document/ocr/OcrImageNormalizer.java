package com.example.knowledgeagent.document.ocr;

import com.example.knowledgeagent.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 将 DOCX 内嵌图片规范化为 OCR sidecar 稳定支持的 PNG，同时过滤明显无效或风险过大的图片。
 */
@Component
@RequiredArgsConstructor
public class OcrImageNormalizer {
    private final OcrProperties ocrProperties;

    /**
     * 执行图片格式归一和基础质量检查；失败时返回跳过原因，由上层写入解析 metadata。
     */
    public NormalizedImage normalize(byte[] imageBytes, String extension) {
        OcrProperties.Docx docx = ocrProperties.resolvedDocx();
        if (imageBytes == null || imageBytes.length == 0) {
            return NormalizedImage.skipped("empty image");
        }
        if (imageBytes.length < docx.resolvedMinBytes()) {
            return NormalizedImage.skipped("image bytes below threshold: " + imageBytes.length);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return NormalizedImage.skipped("unsupported image format: " + extension);
            }
            long pixels = (long) image.getWidth() * image.getHeight();
            if (pixels <= 0) {
                return NormalizedImage.skipped("invalid image size");
            }
            if (pixels > docx.resolvedMaxPixels()) {
                return NormalizedImage.skipped("image pixels exceed limit: " + pixels);
            }
            if (isBlankImage(image)) {
                return NormalizedImage.skipped("blank image");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return NormalizedImage.ready(output.toByteArray(), image.getWidth(), image.getHeight());
        } catch (IOException ex) {
            return NormalizedImage.skipped("image normalize failed: " + ex.getMessage());
        }
    }

    /**
     * 采样判断是否为近似纯色图，避免空白占位图进入 OCR 浪费时间。
     */
    private boolean isBlankImage(BufferedImage image) {
        int stepX = Math.max(1, image.getWidth() / 16);
        int stepY = Math.max(1, image.getHeight() / 16);
        int minR = 255;
        int minG = 255;
        int minB = 255;
        int maxR = 0;
        int maxG = 0;
        int maxB = 0;
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                minR = Math.min(minR, r);
                minG = Math.min(minG, g);
                minB = Math.min(minB, b);
                maxR = Math.max(maxR, r);
                maxG = Math.max(maxG, g);
                maxB = Math.max(maxB, b);
            }
        }
        return maxR - minR < 8 && maxG - minG < 8 && maxB - minB < 8;
    }

    /**
     * 图片归一化结果；ready=false 时 reason 是可写入 metadata 的跳过原因。
     */
    public record NormalizedImage(boolean ready, byte[] bytes, int width, int height, String reason) {
        /**
         * 构造可进入 OCR 的 PNG 图片。
         */
        public static NormalizedImage ready(byte[] bytes, int width, int height) {
            return new NormalizedImage(true, bytes, width, height, null);
        }

        /**
         * 构造跳过结果，调用方会把 reason 记录到 ocrFailures。
         */
        public static NormalizedImage skipped(String reason) {
            return new NormalizedImage(false, new byte[0], 0, 0, reason);
        }
    }
}
