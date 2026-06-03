package com.example.knowledgeagent.document.ocr;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 记录一次 OCR 文本在原始文档中的来源位置，最终写入 ParsedDocument.metadata 便于排查。
 */
public record OcrItem(
        String sourceType,
        Integer pageNumber,
        Integer imageIndex,
        String text,
        double confidence,
        long elapsedMs
) {
    /**
     * 转成可序列化 Map，保持 ParsedDocument.metadata 的现有弱结构扩展方式。
     */
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", sourceType);
        metadata.put("pageNumber", pageNumber);
        metadata.put("imageIndex", imageIndex);
        metadata.put("text", text);
        metadata.put("confidence", confidence);
        metadata.put("elapsedMs", elapsedMs);
        return metadata;
    }
}
