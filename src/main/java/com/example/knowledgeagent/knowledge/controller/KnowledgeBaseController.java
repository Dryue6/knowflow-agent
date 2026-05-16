package com.example.knowledgeagent.knowledge.controller;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.knowledgeagent.knowledge.service.KnowledgeBaseService;
import com.example.knowledgeagent.knowledge.vo.KnowledgeBaseVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建新的知识库。
     */
    @PostMapping
    public ApiResult<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return ApiResult.ok(knowledgeBaseService.createKnowledgeBase(request));
    }

    /**
     * 分页查询知识库列表。
     */
    @GetMapping
    public ApiResult<PageResult<KnowledgeBaseVO>> page(@RequestParam(defaultValue = "1") @Min(1) long page,
                                                       @RequestParam(defaultValue = "10") @Min(1) long size,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResult.ok(knowledgeBaseService.pageKnowledgeBases(page, size, keyword));
    }

    /**
     * 查询知识库详情。
     */
    @GetMapping("/{id}")
    public ApiResult<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return ApiResult.ok(knowledgeBaseService.getKnowledgeBase(id));
    }

    /**
     * 更新知识库名称、描述和状态。
     */
    @PutMapping("/{id}")
    public ApiResult<KnowledgeBaseVO> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return ApiResult.ok(knowledgeBaseService.updateKnowledgeBase(id, request));
    }

    /**
     * 删除知识库，并清理其关联向量数据。
     */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return ApiResult.ok();
    }
}
