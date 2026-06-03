package com.example.knowledgeagent.document.splitter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleDocumentSplitterServiceTest {

    @Test
    void docxOcrBlockStartsIndependentChunk() {
        SimpleDocumentSplitterService splitter = new SimpleDocumentSplitterService();
        String text = "前置正文第一段\n前置正文第二段\n\n"
                + "【图片OCR DOCX 第 1 张 / 正文第1段】物联网环境监测系统\n数据采集层\nSerial.cpp\n\n"
                + "后续正文段落";

        List<TextChunk> chunks = splitter.split(text, 80, 10);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chunks.get(1).content()).startsWith("【图片OCR DOCX 第 1 张 / 正文第1段】");
        assertThat(chunks.get(1).content()).contains("物联网环境监测系统", "Serial.cpp");
        assertThat(chunks.get(1).splitStrategy()).isEqualTo("DOCX_OCR_BLOCK");
    }

    @Test
    void longDocxOcrBlockKeepsPrefixOnEveryChunk() {
        SimpleDocumentSplitterService splitter = new SimpleDocumentSplitterService();
        String text = "【图片OCR DOCX 第 2 张 / 正文第3段】"
                + "第一行图片文字\n第二行图片文字\n第三行图片文字\n第四行图片文字\n第五行图片文字";

        List<TextChunk> chunks = splitter.split(text, 45, 5);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content()).startsWith("【图片OCR DOCX 第 2 张 / 正文第3段】"));
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.splitStrategy()).isEqualTo("DOCX_OCR_BLOCK_LONG"));
    }

    @Test
    void headingAndBodyPreferSameChunk() {
        SimpleDocumentSplitterService splitter = new SimpleDocumentSplitterService();
        String text = "一、系统架构\n"
                + "物联网环境监测系统由数据采集层、数据处理层、应用表现层组成，需要保留完整说明。\n\n"
                + "二、部署说明\n"
                + "部署时需要先启动采集模块，再启动查询模块。";

        List<TextChunk> chunks = splitter.split(text, 120, 20);

        assertThat(chunks.get(0).content()).contains("一、系统架构", "数据采集层", "数据处理层");
        assertThat(chunks.get(0).splitStrategy()).isEqualTo("STRUCTURED_BOUNDARY");
    }

    @Test
    void longParagraphSplitsOnSentenceBoundary() {
        SimpleDocumentSplitterService splitter = new SimpleDocumentSplitterService();
        String text = "这是第一句用于填充较长段落。".repeat(8)
                + "这是第二部分用于验证不会优先从句子中间硬切。".repeat(8);

        List<TextChunk> chunks = splitter.split(text, 80, 10);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).content()).endsWith("。");
        assertThat(chunks.get(0).splitStrategy()).isEqualTo("LONG_TEXT_SENTENCE");
    }

    @Test
    void tableRowsStayNearEachOther() {
        SimpleDocumentSplitterService splitter = new SimpleDocumentSplitterService();
        String text = "模块 | 文件 | 说明\n"
                + "串口通信模块 | Serial.cpp | 采集传感器数据\n"
                + "查询模块 | QueryModule.cpp | 提供查询能力";

        List<TextChunk> chunks = splitter.split(text, 180, 20);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("Serial.cpp", "QueryModule.cpp");
        assertThat(chunks.get(0).tokenCount()).isEqualTo((int) Math.ceil(chunks.get(0).content().length() / 4.0));
    }
}
