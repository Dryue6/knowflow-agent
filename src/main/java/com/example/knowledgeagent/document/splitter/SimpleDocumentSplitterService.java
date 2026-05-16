package com.example.knowledgeagent.document.splitter;

import com.example.knowledgeagent.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimpleDocumentSplitterService implements DocumentSplitterService {
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

    private int estimateTokens(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
