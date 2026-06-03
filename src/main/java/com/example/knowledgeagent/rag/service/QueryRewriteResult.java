package com.example.knowledgeagent.rag.service;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 定义 QueryRewriteResult 数据结构，用于在层间传递结构化数据。
 */
public record QueryRewriteResult(
        String originalQuery,
        List<String> queryVariants,
        List<String> coreTerms,
        List<String> phraseTerms,
        List<String> expandedTerms
) {
    /**
     * 处理 allTerms 方法对应的业务逻辑。
     */
    public List<String> allTerms() {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.addAll(coreTerms);
        terms.addAll(phraseTerms);
        terms.addAll(expandedTerms);
        return terms.stream().toList();
    }
}
