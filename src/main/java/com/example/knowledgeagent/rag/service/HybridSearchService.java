package com.example.knowledgeagent.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 定义 HybridSearchService 组件，承载对应模块的业务职责。
 */
public class HybridSearchService {
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;

    /**
     * 执行关键词召回，并为直接命中的切片补充相邻切片作为上下文候选。
     */
    public List<RagSearchItemVO> search(Long knowledgeBaseId, QueryRewriteResult rewrite, int limit, double keywordMinScore) {
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(knowledgeBaseId != null, DocumentChunk::getKnowledgeBaseId, knowledgeBaseId));
        if (chunks.isEmpty() || rewrite.allTerms().isEmpty()) {
            return List.of();
        }
        Map<Long, Document> documents = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .distinct()
                .map(documentMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Document::getId, document -> document));

        Map<Long, DocumentChunk> byId = chunks.stream().collect(Collectors.toMap(DocumentChunk::getId, chunk -> chunk));
        Map<String, DocumentChunk> byDocumentAndIndex = chunks.stream()
                .collect(Collectors.toMap(chunk -> key(chunk.getDocumentId(), chunk.getChunkIndex()), chunk -> chunk, (a, b) -> a));

        // 直接命中只看当前切片内容和文档标题，分数达到阈值后进入候选池。
        List<RagSearchItemVO> directMatches = chunks.stream()
                .map(chunk -> toResult(chunk, documents.get(chunk.getDocumentId()), rewrite))
                .filter(item -> item.score() >= keywordMinScore)
                .sorted(Comparator.comparingDouble(RagSearchItemVO::score).reversed())
                .limit(limit)
                .toList();

        // 相邻切片没有直接命中也可能承载答案上下文，因此按来源分数折扣后补入。
        List<RagSearchItemVO> adjacentMatches = directMatches.stream()
                .flatMap(item -> List.of(item.chunkIndex() - 1, item.chunkIndex() + 1).stream()
                        .map(index -> byDocumentAndIndex.get(key(item.documentId(), index)))
                        .filter(Objects::nonNull)
                        .filter(chunk -> !chunk.getId().equals(item.chunkId()))
                        .map(chunk -> toAdjacentResult(chunk, documents.get(chunk.getDocumentId()), item.score())))
                .filter(item -> item.score() >= keywordMinScore)
                .filter(item -> byId.containsKey(item.chunkId()))
                .toList();

        return merge(directMatches, adjacentMatches).stream()
                .sorted(Comparator.comparingDouble(RagSearchItemVO::score).reversed())
                .limit(limit)
                .toList();
    }

    @SafeVarargs
    /**
     * 合并直接命中和相邻切片，同一 chunk 保留最高分。
     */
    private final List<RagSearchItemVO> merge(List<RagSearchItemVO>... groups) {
        return List.of(groups).stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(RagSearchItemVO::chunkId, item -> item, (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .toList();
    }

    /**
     * 根据 phrase/core/expanded 三类术语计算关键词匹配分。
     */
    private RagSearchItemVO toResult(DocumentChunk chunk, Document document, QueryRewriteResult rewrite) {
        String content = normalize(chunk.getContent());
        String documentName = document == null ? "未知文档" : document.getOriginalFileName();
        String title = normalize(documentName + " " + (document == null ? "" : document.getTitle()) + " " + chunk.getSectionTitle());
        double score = 0;

        for (String phrase : rewrite.phraseTerms()) {
            String term = normalize(phrase);
            if (content.contains(term)) {
                score += 0.75;
            }
            if (title.contains(term)) {
                score += 0.5;
            }
        }
        for (String termValue : rewrite.coreTerms()) {
            String term = normalize(termValue);
            if (content.contains(term)) {
                score += 0.65;
            }
            if (title.contains(term)) {
                score += 0.45;
            }
        }
        for (String termValue : rewrite.expandedTerms()) {
            String term = normalize(termValue);
            if (content.contains(term)) {
                score += 0.35;
            }
            if (title.contains(term)) {
                score += 0.25;
            }
        }

        double positionBoost = chunk.getChunkIndex() != null && chunk.getChunkIndex() <= 2 ? 0.05 : 0;
        return new RagSearchItemVO(chunk.getDocumentId(), documentName, chunk.getId(), chunk.getChunkIndex(), chunk.getContent(), Math.min(1, score + positionBoost));
    }

    /**
     * 构建相邻切片候选，分数低于直接命中以体现上下文补充属性。
     */
    private RagSearchItemVO toAdjacentResult(DocumentChunk chunk, Document document, double sourceScore) {
        String documentName = document == null ? "未知文档" : document.getOriginalFileName();
        return new RagSearchItemVO(chunk.getDocumentId(), documentName, chunk.getId(), chunk.getChunkIndex(), chunk.getContent(), Math.min(0.65, sourceScore * 0.75));
    }

    /**
     * 生成文档内切片位置 key，用于快速查找前后相邻切片。
     */
    private String key(Long documentId, Integer chunkIndex) {
        return documentId + ":" + chunkIndex;
    }

    /**
     * 归一化关键词匹配文本，忽略大小写和空白差异。
     */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
