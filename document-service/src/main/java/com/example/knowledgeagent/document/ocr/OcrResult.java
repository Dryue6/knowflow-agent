package com.example.knowledgeagent.document.ocr;

/**
 * OCR 单次识别结果，success=false 表示调用失败或结果被置信度阈值过滤。
 */
public record OcrResult(
        boolean success,
        String text,
        double confidence,
        long elapsedMs,
        String error
) {
    /**
     * 构造成功结果，文本会在上层按文件位置拼入解析内容。
     */
    public static OcrResult success(String text, double confidence, long elapsedMs) {
        return new OcrResult(true, text, confidence, elapsedMs, null);
    }

    /**
     * 构造失败结果，调用方用它统计失败次数并按无 OCR 内容降级。
     */
    public static OcrResult failure(String error, long elapsedMs) {
        return new OcrResult(false, "", 0.0, elapsedMs, error);
    }
}
