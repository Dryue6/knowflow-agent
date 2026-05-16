package com.example.knowledgeagent.knowledge.service;

import com.example.knowledgeagent.common.api.PageResult;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.knowledgeagent.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.knowledgeagent.knowledge.vo.KnowledgeBaseVO;

public interface KnowledgeBaseService {
    /**
     * 创建知识库。
     */
    KnowledgeBaseVO createKnowledgeBase(KnowledgeBaseCreateRequest request);

    /**
     * 更新知识库基础信息。
     */
    KnowledgeBaseVO updateKnowledgeBase(Long id, KnowledgeBaseUpdateRequest request);

    /**
     * 删除知识库。
     */
    void deleteKnowledgeBase(Long id);

    /**
     * 查询知识库详情。
     */
    KnowledgeBaseVO getKnowledgeBase(Long id);

    /**
     * 分页查询知识库。
     */
    PageResult<KnowledgeBaseVO> pageKnowledgeBases(long page, long size, String keyword);

    /**
     * 刷新知识库下文档数和切片数统计。
     */
    void updateStatistics(Long knowledgeBaseId);
}
