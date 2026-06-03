package com.example.knowledgeagent.rag.service;

import com.example.knowledgeagent.rag.vo.RagSearchItemVO;

import java.util.List;

/**
 * 定义 RerankService 接口，约定该模块对外提供的能力。
 */
public interface RerankService {
    /**
     * 声明  能力，由具体实现类完成业务处理。
     */
    List<RagSearchItemVO> rerank(String query, QueryRewriteResult rewrite, List<RagSearchItemVO> candidates);
}
