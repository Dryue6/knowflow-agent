package com.example.knowledgeagent.rag.service.impl;

import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.rag.service.QueryRewriteResult;
import com.example.knowledgeagent.rag.service.RerankService;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
/**
 * 定义 LocalRerankService 组件，承载对应模块的业务职责。
 */
public class LocalRerankService implements RerankService {
    private final RagProperties ragProperties;

    @Override
    /**
     * 处理 rerank 方法对应的业务逻辑。
     */
    public List<RagSearchItemVO> rerank(String query, QueryRewriteResult rewrite, List<RagSearchItemVO> candidates) {
        if (ragProperties.rerank() != null && !ragProperties.rerank().enabled()) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(RagSearchItemVO::score).reversed())
                    .toList();
        }
        return candidates.stream()
                .map(item -> withScore(item, rerankScore(item, rewrite)))
                .sorted(Comparator.comparingDouble(RagSearchItemVO::score).reversed())
                .toList();
    }

    /**
     * 处理 rerankScore 方法对应的业务逻辑。
     */
    private double rerankScore(RagSearchItemVO item, QueryRewriteResult rewrite) {
        String content = normalize(item.content());
        String title = normalize(item.documentName());
        double score = Math.max(0, item.score()) * 0.55;

        for (String phrase : rewrite.phraseTerms()) {
            String term = normalize(phrase);
            if (content.contains(term)) {
                score += 0.28;
            }
            if (title.contains(term)) {
                score += 0.12;
            }
        }
        for (String core : rewrite.coreTerms()) {
            String term = normalize(core);
            if (content.contains(term)) {
                score += 0.25;
            }
            if (title.contains(term)) {
                score += 0.1;
            }
        }
        for (String expanded : rewrite.expandedTerms()) {
            String term = normalize(expanded);
            if (content.contains(term)) {
                score += 0.1;
            }
        }
        if (item.chunkIndex() != null && item.chunkIndex() <= 2) {
            score += 0.03;
        }
        return Math.min(1, score);
    }

    /**
     * 处理 withScore 方法对应的业务逻辑。
     */
    private RagSearchItemVO withScore(RagSearchItemVO item, double score) {
        return new RagSearchItemVO(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(), item.content(), score);
    }

    /**
     * 处理 normalize 方法对应的业务逻辑。
     */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
