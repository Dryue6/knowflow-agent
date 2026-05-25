package com.example.knowledgeagent.rag.controller;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagSearchResponseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag")
/**
 * 定义 RagController 组件，承载对应模块的业务职责。
 */
public class RagController {
    private final RagService ragService;

    /**
     * 执行 RAG 向量检索，返回相似文档切片。
     */
    @PostMapping("/search")
    public ApiResult<RagSearchResponseVO> search(@Valid @RequestBody RagSearchRequest request) {
        return ApiResult.ok(ragService.retrieve(request));
    }

    /**
     * 执行非流式 RAG 问答，返回答案和引用来源。
     */
    @PostMapping("/ask")
    public ApiResult<RagAnswerVO> ask(@Valid @RequestBody RagAskRequest request) {
        return ApiResult.ok(ragService.ask(request, List.of()));

    }
}
