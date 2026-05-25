package com.example.knowledgeagent.document.controller;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.document.dto.DocumentUploadResponse;
import com.example.knowledgeagent.document.dto.UpdateDocumentConstraintRequest;
import com.example.knowledgeagent.document.service.DocumentService;
import com.example.knowledgeagent.document.vo.DocumentChunkVO;
import com.example.knowledgeagent.document.vo.DocumentFileResource;
import com.example.knowledgeagent.document.vo.DocumentPreviewTextVO;
import com.example.knowledgeagent.document.vo.DocumentVO;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
/**
 * 定义 DocumentController 组件，承载对应模块的业务职责。
 */
public class DocumentController {
    private final DocumentService documentService;

    /**
     * 上传文档到指定知识库，并创建异步索引任务。
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents/upload")
    public ApiResult<DocumentUploadResponse> upload(@PathVariable Long knowledgeBaseId, @RequestPart("file") MultipartFile file) {
        return ApiResult.ok(documentService.uploadDocument(knowledgeBaseId, file));
    }

    /**
     * 分页查询指定知识库下的文档。
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResult<PageResult<DocumentVO>> page(@PathVariable Long knowledgeBaseId,
                                                  @RequestParam(defaultValue = "1") @Min(1) long page,
                                                  @RequestParam(defaultValue = "10") @Min(1) long size,
                                                  @RequestParam(required = false) String keyword) {
        return ApiResult.ok(documentService.pageDocuments(knowledgeBaseId, page, size, keyword));
    }

    /**
     * 查询文档详情。
     */
    @GetMapping("/documents/{documentId}")
    public ApiResult<DocumentVO> detail(@PathVariable Long documentId) {
        return ApiResult.ok(documentService.getDocument(documentId));
    }

    /**
     * 删除文档、切片和向量数据。
     */
    @DeleteMapping("/documents/{documentId}")
    public ApiResult<Void> delete(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return ApiResult.ok();
    }

    /**
     * 对已有文档重新创建索引任务。
     */
    @PostMapping("/documents/{documentId}/reindex")
    public ApiResult<DocumentUploadResponse> reindex(@PathVariable Long documentId) {
        return ApiResult.ok(documentService.reindexDocument(documentId));
    }

    /**
     * 分页查询文档切片。
     */
    @GetMapping("/documents/{documentId}/chunks")
    public ApiResult<PageResult<DocumentChunkVO>> chunks(@PathVariable Long documentId,
                                                         @RequestParam(defaultValue = "1") @Min(1) long page,
                                                         @RequestParam(defaultValue = "10") @Min(1) long size) {
        return ApiResult.ok(documentService.listDocumentChunks(documentId, page, size));
    }

    @GetMapping("/documents/{documentId}/file")
    /**
     * 处理 file 方法对应的业务逻辑。
     */
    public ResponseEntity<?> file(@PathVariable Long documentId) {
        return fileResponse(documentService.getDocumentFile(documentId), true);
    }

    @GetMapping("/documents/{documentId}/download")
    /**
     * 处理 download 方法对应的业务逻辑。
     */
    public ResponseEntity<?> download(@PathVariable Long documentId) {
        return fileResponse(documentService.getDocumentFile(documentId), false);
    }

    @GetMapping("/documents/{documentId}/preview-text")
    /**
     * 处理 previewText 方法对应的业务逻辑。
     */
    public ApiResult<DocumentPreviewTextVO> previewText(@PathVariable Long documentId) {
        return ApiResult.ok(documentService.previewText(documentId));
    }

    @PatchMapping("/documents/{documentId}/constraint")
    public ApiResult<DocumentVO> updateConstraint(@PathVariable Long documentId,
                                                  @Validated @RequestBody UpdateDocumentConstraintRequest request) {
        return ApiResult.ok(documentService.updateConstraint(documentId, request));
    }

    /**
     * 处理 fileResponse 方法对应的业务逻辑。
     */
    private ResponseEntity<?> fileResponse(DocumentFileResource file, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }
}
