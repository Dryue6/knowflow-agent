package com.example.knowledgeagent.document.ocr;

/**
 * 封装文档解析阶段的图片 OCR 能力，解析器只关心输入图片和输出文本，不直接依赖具体 OCR 引擎。
 */
public interface OcrService {
    /**
     * 判断 OCR 是否启用；未启用时解析器应跳过图片识别以保持原有性能。
     */
    boolean isEnabled();

    /**
     * 识别单张图片并返回文本、置信度和耗时；失败时必须返回 failure 结果而不是抛出异常。
     */
    OcrResult recognize(byte[] imageBytes, String fileName);
}
