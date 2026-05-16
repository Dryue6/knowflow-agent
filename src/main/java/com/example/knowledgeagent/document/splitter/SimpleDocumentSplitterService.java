package com.example.knowledgeagent.document.splitter;

import com.example.knowledgeagent.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimpleDocumentSplitterService implements DocumentSplitterService {
    /**
     * 按 chunkSize 和 overlap 切分文本。
     * <p>
     * 切片时优先在换行处断开，减少段落被硬切开的概率；下一片从 end-overlap 开始，
     * 保留少量上下文重叠，提升 RAG 召回后上下文连续性。
     */
    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw BusinessException.badRequest("切片参数不合法");
        }
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            if (end < normalized.length()) {
                // 如果 chunkSize 范围内后半段存在换行，就在换行处结束，尽量保持自然段完整。
                int newline = normalized.lastIndexOf('\n', end);
                if (newline > start + chunkSize / 2) {
                    end = newline + 1;
                }
            }
            String content = normalized.substring(start, end).trim();
            if (StringUtils.hasText(content)) {
                chunks.add(new TextChunk(index++, content, estimateTokens(content)));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    /**
     * 粗略估算 token 数。
     * <p>
     * 当前不引入 tokenizer，按 4 字符约 1 token 估算，用于前端展示和后续限流参考。
     */
    private int estimateTokens(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
