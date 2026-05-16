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
public class RagController {
    private final RagService ragService;

    @PostMapping("/search")
    public ApiResult<RagSearchResponseVO> search(@Valid @RequestBody RagSearchRequest request) {
        return ApiResult.ok(ragService.retrieve(request));
    }

    @PostMapping("/ask")
    public ApiResult<RagAnswerVO> ask(@Valid @RequestBody RagAskRequest request) {
        return ApiResult.ok(ragService.ask(request, List.of()));
    }
}
