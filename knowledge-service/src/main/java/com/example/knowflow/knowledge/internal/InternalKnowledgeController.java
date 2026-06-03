package com.example.knowflow.knowledge.internal;

import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.knowledge.service.KnowledgeBaseService;
import com.example.knowledgeagent.knowledge.service.impl.KnowledgeBaseServiceImpl;
import com.example.knowledgeagent.knowledge.vo.KnowledgeBaseVO;
import com.example.knowflow.contract.dto.KnowledgeBaseInfo;
import com.example.knowflow.contract.dto.KnowledgeStatisticsUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库服务内部接口，供其他微服务通过 OpenFeign 调用。
 */
@RestController
@RequestMapping("/internal/knowledge-bases")
public class InternalKnowledgeController {
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 注入知识库领域服务。
     */
    public InternalKnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 查询知识库详情，用于下游服务校验业务 ID。
     */
    @GetMapping("/{id}")
    public ApiResult<KnowledgeBaseInfo> detail(@PathVariable Long id) {
        KnowledgeBaseVO vo = knowledgeBaseService.getKnowledgeBase(id);
        return ApiResult.ok(new KnowledgeBaseInfo(
                vo.id(),
                vo.name(),
                vo.description(),
                vo.status().name(),
                vo.documentCount(),
                vo.chunkCount(),
                vo.createdAt(),
                vo.updatedAt()));
    }

    /**
     * 接收 document-service 推送的统计更新请求，不再反查 document schema。
     */
    @PostMapping("/statistics")
    public ApiResult<Void> updateStatistics(@RequestBody KnowledgeStatisticsUpdateRequest request) {
        if (knowledgeBaseService instanceof KnowledgeBaseServiceImpl impl) {
            impl.updateStatistics(request.knowledgeBaseId(), request.documentCount(), request.chunkCount());
        } else {
            knowledgeBaseService.updateStatistics(request.knowledgeBaseId());
        }
        return ApiResult.ok();
    }
}
