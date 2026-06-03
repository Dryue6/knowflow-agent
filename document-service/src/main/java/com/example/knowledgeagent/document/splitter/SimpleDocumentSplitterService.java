package com.example.knowledgeagent.document.splitter;

import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.common.util.TextSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
/**
 * 定义 SimpleDocumentSplitterService 组件，承载对应模块的业务职责。
 */
public class SimpleDocumentSplitterService implements DocumentSplitterService {
    private static final String DOCX_OCR_MARKER = "【图片OCR DOCX";
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6}\\s+.+|第.{1,12}[章节篇部分].*|[一二三四五六七八九十]+[、.．].+|\\d+(\\.\\d+)*[、.．\\s].+)$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^([\\-＊*•]\\s+|\\d+[.)、]\\s*|[一二三四五六七八九十]+[.)、]\\s*).+");
    private static final String STRUCTURED_STRATEGY = "STRUCTURED_BOUNDARY";
    private static final String LONG_TEXT_STRATEGY = "LONG_TEXT_SENTENCE";
    private static final String OCR_STRATEGY = "DOCX_OCR_BLOCK";
    private static final String LONG_OCR_STRATEGY = "DOCX_OCR_BLOCK_LONG";

    /**
     * 按 chunkSize 和 overlap 切分文本。
     * <p>
     * 新策略优先保留标题、段落、列表、表格行和 OCR 段落的自然边界；只有单个语义单元过长时，
     * 才退化到句号、分号、换行等边界切分。overlap 仍以字符数表示，用于跨 chunk 保留少量上下文。
     */
    @Override
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw BusinessException.badRequest("切片参数不合法");
        }
        String normalized = text == null ? "" : TextSanitizer.removeNullBytes(text).replace("\r\n", "\n").trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        if (normalized.contains(DOCX_OCR_MARKER)) {
            return splitWithDocxOcrBlocks(normalized, chunkSize, overlap);
        }
        return splitStructuredText(normalized, 0, chunkSize, overlap, new ChunkIndex());
    }

    /**
     * DOCX 图片 OCR 块需要尽量独立成片，避免被长正文或代码稀释导致 RAG 难以召回图片文字。
     */
    private List<TextChunk> splitWithDocxOcrBlocks(String normalized, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        ChunkIndex index = new ChunkIndex();
        int cursor = 0;
        while (cursor < normalized.length()) {
            int marker = normalized.indexOf(DOCX_OCR_MARKER, cursor);
            if (marker < 0) {
                chunks.addAll(splitStructuredText(normalized.substring(cursor), cursor, chunkSize, overlap, index));
                break;
            }
            if (marker > cursor) {
                chunks.addAll(splitStructuredText(normalized.substring(cursor, marker), cursor, chunkSize, overlap, index));
            }
            int blockEnd = normalized.indexOf("\n\n", marker);
            if (blockEnd < 0) {
                blockEnd = normalized.length();
            }
            String ocrBlock = normalized.substring(marker, blockEnd).trim();
            chunks.addAll(splitOcrBlock(ocrBlock, marker, chunkSize, index));
            cursor = blockEnd;
            while (cursor < normalized.length() && normalized.charAt(cursor) == '\n') {
                cursor++;
            }
        }
        return chunks;
    }

    /**
     * 短 OCR 块独立成片；长 OCR 块拆分时为每片保留 OCR 前缀，方便数据库和召回结果识别来源。
     */
    private List<TextChunk> splitOcrBlock(String ocrBlock, int baseOffset, int chunkSize, ChunkIndex index) {
        if (!StringUtils.hasText(ocrBlock)) {
            return List.of();
        }
        if (ocrBlock.length() <= chunkSize) {
            return List.of(new TextChunk(index.next(), ocrBlock, estimateTokens(ocrBlock),
                    baseOffset, baseOffset + ocrBlock.length(), OCR_STRATEGY));
        }
        int firstLineEnd = ocrBlock.indexOf('\n');
        String prefix = firstLineEnd < 0 ? ocrBlock.substring(0, Math.min(ocrBlock.length(), chunkSize / 2))
                : ocrBlock.substring(0, firstLineEnd);
        String body = firstLineEnd < 0 ? ocrBlock.substring(prefix.length()) : ocrBlock.substring(firstLineEnd + 1);
        int safeBodySize = Math.max(1, chunkSize - prefix.length() - 1);
        List<TextChunk> chunks = new ArrayList<>();
        int bodyCursor = 0;
        while (bodyCursor < body.length()) {
            int end = Math.min(body.length(), bodyCursor + safeBodySize);
            String content = (prefix + "\n" + body.substring(bodyCursor, end)).trim();
            if (StringUtils.hasText(content)) {
                chunks.add(new TextChunk(index.next(), content, estimateTokens(content),
                        baseOffset + Math.max(0, firstLineEnd + 1 + bodyCursor),
                        baseOffset + Math.max(0, firstLineEnd + 1 + end), LONG_OCR_STRATEGY));
            }
            bodyCursor = end;
        }
        return chunks;
    }

    /**
     * 普通文本按结构单元聚合：标题、列表、表格行会先作为独立语义单元，再由打包逻辑合并到合适大小。
     * 这样能避免 800 字符级别的硬切片把标题和正文拆散。
     */
    private List<TextChunk> splitStructuredText(String normalized, int baseOffset, int chunkSize, int overlap, ChunkIndex index) {
        String plain = normalized == null ? "" : normalized.trim();
        if (!StringUtils.hasText(plain)) {
            return List.of();
        }
        return packUnits(buildSemanticUnits(plain, baseOffset), chunkSize, overlap, index);
    }

    /**
     * 将文本拆成尽量贴近文档结构的语义单元，后续再按大小合并，避免直接按固定字符窗口切断语义。
     */
    private List<TextUnit> buildSemanticUnits(String text, int baseOffset) {
        List<TextUnit> units = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        int paragraphStart = -1;
        int paragraphEnd = -1;
        int cursor = 0;
        for (String line : lines) {
            int lineStart = cursor;
            int lineEnd = cursor + line.length();
            cursor = lineEnd + 1;
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                flushParagraph(units, paragraph, baseOffset + paragraphStart, baseOffset + paragraphEnd);
                paragraphStart = -1;
                paragraphEnd = -1;
                continue;
            }
            if (isBoundaryLine(trimmed)) {
                flushParagraph(units, paragraph, baseOffset + paragraphStart, baseOffset + paragraphEnd);
                paragraphStart = -1;
                paragraphEnd = -1;
                units.add(new TextUnit(line, baseOffset + lineStart, baseOffset + lineEnd));
                continue;
            }
            if (paragraph.isEmpty()) {
                paragraphStart = lineStart;
            } else {
                paragraph.append('\n');
            }
            paragraph.append(line);
            paragraphEnd = lineEnd;
        }
        flushParagraph(units, paragraph, baseOffset + paragraphStart, baseOffset + paragraphEnd);
        return units;
    }

    /**
     * 将当前段落写入语义单元列表；空段落不会生成 chunk，避免空白内容进入向量库。
     */
    private void flushParagraph(List<TextUnit> units, StringBuilder paragraph, int startOffset, int endOffset) {
        String content = paragraph.toString().trim();
        if (StringUtils.hasText(content)) {
            units.add(new TextUnit(content, startOffset, endOffset));
        }
        paragraph.setLength(0);
    }

    /**
     * 判断一行是否应作为结构边界。标题、列表和表格行通常承载独立语义，应优先在这些位置切分。
     */
    private boolean isBoundaryLine(String text) {
        return isHeading(text) || LIST_PATTERN.matcher(text).matches() || isTableLine(text);
    }

    /**
     * 判断文档标题行，配合 DocumentIndexServiceImpl 中的章节识别规则保持口径接近。
     */
    private boolean isHeading(String text) {
        return text.length() <= 100 && HEADING_PATTERN.matcher(text).matches();
    }

    /**
     * 表格从 DOCX/PDF 抽取后常表现为带竖线、制表符或连续多空格的行，按行保留可减少单元格错位。
     */
    private boolean isTableLine(String text) {
        return text.contains("\t") || text.contains("|") || text.matches(".+\\s{2,}.+");
    }

    /**
     * 将结构单元打包成 chunk。默认配置下 chunk 目标约 1200-2200 字符，单元过长时才进行句子级切分。
     */
    private List<TextChunk> packUnits(List<TextUnit> units, int chunkSize, int overlap, ChunkIndex index) {
        List<TextChunk> chunks = new ArrayList<>();
        int hardMax = hardMaxChars(chunkSize, overlap);
        StringBuilder buffer = new StringBuilder();
        int bufferStart = -1;
        int bufferEnd = -1;
        for (TextUnit unit : units) {
            if (unit.content().length() > hardMax) {
                if (!buffer.isEmpty()) {
                    addChunk(chunks, index, buffer.toString(), bufferStart, bufferEnd, STRUCTURED_STRATEGY);
                    buffer.setLength(0);
                    bufferStart = -1;
                }
                chunks.addAll(splitLongTextUnit(unit, chunkSize, overlap, index));
                bufferEnd = unit.endOffset();
                continue;
            }
            int nextLength = buffer.isEmpty() ? unit.content().length() : buffer.length() + 1 + unit.content().length();
            if (!buffer.isEmpty() && nextLength > hardMax) {
                addChunk(chunks, index, buffer.toString(), bufferStart, bufferEnd, STRUCTURED_STRATEGY);
                String carry = overlapSuffix(buffer.toString(), overlap);
                buffer.setLength(0);
                if (StringUtils.hasText(carry)) {
                    buffer.append(carry).append('\n');
                    bufferStart = Math.max(bufferStart, bufferEnd - carry.length());
                } else {
                    bufferStart = -1;
                }
            }
            if (buffer.isEmpty()) {
                bufferStart = unit.startOffset();
            } else if (buffer.charAt(buffer.length() - 1) != '\n') {
                buffer.append('\n');
            }
            buffer.append(unit.content());
            bufferEnd = unit.endOffset();
        }
        if (!buffer.isEmpty()) {
            addChunk(chunks, index, buffer.toString(), bufferStart, bufferEnd, STRUCTURED_STRATEGY);
        }
        return chunks;
    }

    /**
     * 允许结构化切片在配置 chunkSize 上方保留少量弹性，避免刚好超过阈值就拆散一个自然段。
     */
    private int hardMaxChars(int chunkSize, int overlap) {
        return chunkSize + Math.max(overlap, chunkSize / 5);
    }

    /**
     * 单个段落或表格单元过长时，优先在句号、分号、换行等边界切分，最后才退回固定长度。
     */
    private List<TextChunk> splitLongTextUnit(TextUnit unit, int chunkSize, int overlap, ChunkIndex index) {
        List<TextChunk> chunks = new ArrayList<>();
        int hardMax = hardMaxChars(chunkSize, overlap);
        int start = 0;
        while (start < unit.content().length()) {
            int end = Math.min(unit.content().length(), start + hardMax);
            if (end < unit.content().length()) {
                end = bestSentenceBoundary(unit.content(), start, end, chunkSize / 2);
            }
            String content = unit.content().substring(start, end).trim();
            if (StringUtils.hasText(content)) {
                chunks.add(new TextChunk(index.next(), content, estimateTokens(content),
                        unit.startOffset() + start, unit.startOffset() + end, LONG_TEXT_STRATEGY));
            }
            if (end >= unit.content().length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    /**
     * 在候选窗口后半段寻找最自然的句子边界，降低长段落被截断后的阅读割裂感。
     */
    private int bestSentenceBoundary(String text, int start, int end, int minOffset) {
        int lowerBound = start + Math.max(1, minOffset);
        String delimiters = "。！？；;.!?\n";
        for (int i = end - 1; i >= lowerBound; i--) {
            if (delimiters.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        int comma = Math.max(text.lastIndexOf('，', end - 1), text.lastIndexOf(',', end - 1));
        if (comma >= lowerBound) {
            return comma + 1;
        }
        return end;
    }

    /**
     * 根据 overlap 生成下一片的上下文前缀，优先从换行后开始，避免把前一片尾部截成半句话。
     */
    private String overlapSuffix(String content, int overlap) {
        if (overlap <= 0 || content.length() <= overlap) {
            return "";
        }
        int start = content.length() - overlap;
        int newline = content.indexOf('\n', start);
        if (newline >= 0 && newline + 1 < content.length()) {
            start = newline + 1;
        }
        return content.substring(start).trim();
    }

    /**
     * 统一追加 chunk，保证 token 估算和策略字段一致写入。
     */
    private void addChunk(List<TextChunk> chunks, ChunkIndex index, String content, int startOffset, int endOffset,
                          String splitStrategy) {
        String normalized = content == null ? "" : content.trim();
        if (StringUtils.hasText(normalized)) {
            chunks.add(new TextChunk(index.next(), normalized, estimateTokens(normalized), startOffset, endOffset,
                    splitStrategy));
        }
    }

    /**
     * 粗略估算 token 数。
     * <p>
     * 当前不引入 tokenizer，按 4 字符约 1 token 估算，用于前端展示和后续限流参考。
     */
    private int estimateTokens(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    /**
     * 在多段拆分之间共享递增 chunk 序号。
     */
    private static class ChunkIndex {
        private int value;

        private int next() {
            return value++;
        }
    }

    /**
     * 语义单元记录内容及其在原始解析文本中的偏移，用于后续页码、段落定位。
     */
    private record TextUnit(String content, int startOffset, int endOffset) {
    }
}
