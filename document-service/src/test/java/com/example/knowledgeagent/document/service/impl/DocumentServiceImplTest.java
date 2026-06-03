package com.example.knowledgeagent.document.service.impl;

import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.enums.FileType;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.parser.DocumentParserService;
import com.example.knowledgeagent.document.parser.ParsedDocument;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.document.vo.DocumentPreviewTextVO;
import com.example.knowledgeagent.job.DocumentIndexJobService;
import com.example.knowledgeagent.storage.FileStorageService;
import com.example.knowledgeagent.storage.StoredFileMaterialization;
import com.example.knowflow.contract.client.KnowledgeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentChunkMapper documentChunkMapper;
    @Mock
    private KnowledgeClient knowledgeClient;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private DocumentIndexJobService documentIndexJobService;
    @Mock
    private DocumentParserService documentParserService;

    @Test
    void docxPreviewUsesIndexedChunksAndKeepsOcrText() {
        Document document = document(94L, FileType.DOCX);
        when(documentMapper.selectById(94L)).thenReturn(document);
        when(documentChunkMapper.selectList(any())).thenReturn(List.of(
                chunk(0, "正文开头"),
                chunk(1, "【图片OCR DOCX 第 1 张 / 正文第1段】物联网环境监测系统\nSerial.cpp")));

        DocumentPreviewTextVO preview = service().previewText(94L);

        assertThat(preview.content())
                .contains("正文开头")
                .contains("---")
                .contains("【图片OCR DOCX 第 1 张 / 正文第1段】")
                .contains("Serial.cpp");
        // 已索引 chunk 是 RAG 的真实数据来源，预览命中后不能再读取原文件或重新触发 OCR 解析。
        verify(fileStorageService, never()).materialize(any(), any());
        verify(documentParserService, never()).parse(any(), any());
    }

    @Test
    void docxPreviewFallsBackToParserWhenChunksAreMissing() throws Exception {
        Document document = document(95L, FileType.DOCX);
        Path temp = Files.createTempFile("docx-preview", ".docx");
        when(documentMapper.selectById(95L)).thenReturn(document);
        when(documentChunkMapper.selectList(any())).thenReturn(List.of());
        when(fileStorageService.materialize("minio://bucket/doc.docx", "doc.docx"))
                .thenReturn(new StoredFileMaterialization(temp, true));
        when(documentParserService.parse(eq(temp.toString()), eq(FileType.DOCX)))
                .thenReturn(new ParsedDocument("doc.docx", "临时解析文本", Map.of()));

        DocumentPreviewTextVO preview = service().previewText(95L);

        assertThat(preview.content()).isEqualTo("临时解析文本");
        verify(documentParserService).parse(eq(temp.toString()), eq(FileType.DOCX));
    }

    @Test
    void pdfPreviewTextStillRequiresOriginalFilePreview() {
        when(documentMapper.selectById(96L)).thenReturn(document(96L, FileType.PDF));

        assertThatThrownBy(() -> service().previewText(96L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 文档请使用原文件预览");
    }

    private DocumentServiceImpl service() {
        return new DocumentServiceImpl(documentMapper, documentChunkMapper, knowledgeClient, fileStorageService,
                null, vectorStoreService, documentIndexJobService, documentParserService);
    }

    private Document document(Long id, FileType fileType) {
        Document document = new Document();
        document.setId(id);
        document.setKnowledgeBaseId(3L);
        document.setOriginalFileName(fileType == FileType.DOCX ? "doc.docx" : "doc.pdf");
        document.setFilePath(fileType == FileType.DOCX ? "minio://bucket/doc.docx" : "minio://bucket/doc.pdf");
        document.setFileType(fileType);
        document.setStatus(DocumentStatus.INDEXED);
        return document;
    }

    private DocumentChunk chunk(int index, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        return chunk;
    }
}
