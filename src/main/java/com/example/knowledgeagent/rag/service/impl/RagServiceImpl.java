package com.example.knowledgeagent.rag.service.impl;

import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.document.embedding.EmbeddingService;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentConstraintLevel;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.service.VectorSearchResult;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.prompt.RagPromptBuilder;
import com.example.knowledgeagent.rag.service.ChatModelService;
import com.example.knowledgeagent.rag.service.HybridSearchService;
import com.example.knowledgeagent.rag.service.QueryRewriteResult;
import com.example.knowledgeagent.rag.service.QueryRewriteService;
import com.example.knowledgeagent.rag.service.RagCitationService;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.service.RerankService;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import com.example.knowledgeagent.rag.vo.RagSearchResponseVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 定义 RagServiceImpl 组件，承载对应模块的业务职责。
 */
public class RagServiceImpl implements RagService {
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final HybridSearchService hybridSearchService;
    private final QueryRewriteService queryRewriteService;
    private final RerankService rerankService;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final RagProperties ragProperties;
    private final RagPromptBuilder promptBuilder;
    private final ChatModelService chatModelService;
    private final RagCitationService citationService;

    @Override
    /**
     * 执行 RAG 检索流程，合并向量召回、关键词召回和重排结果。
     */
    public RagSearchResponseVO retrieve(RagSearchRequest request) {
        int topK = request.topK() == null ? ragProperties.topK() : request.topK();
        int candidateTopK = Math.max(topK, ragProperties.candidateTopK());
        QueryRewriteResult rewrite = queryRewriteService.rewrite(request.query());
        List<RagSearchItemVO> vectorCandidates = vectorCandidates(request, rewrite, candidateTopK);
        List<RagSearchItemVO> keywordCandidates = hybridSearchService.search(request.knowledgeBaseId(), rewrite, candidateTopK, ragProperties.keywordMinScore());
        List<RagSearchItemVO> chunks = rerankService.rerank(request.query(), rewrite, mergeCandidates(vectorCandidates, keywordCandidates)).stream()
                .filter(item -> item.score() >= ragProperties.finalMinScore())
                .limit(topK)
                .toList();
        return new RagSearchResponseVO(request.query(), chunks);
    }

    @Override
    /**
     * 执行非流式问答，先检索上下文再调用聊天模型生成答案。
     */
    public RagAnswerVO ask(RagAskRequest request, List<ChatHistoryMessage> history) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        RagContext context = buildContext(request.knowledgeBaseId(), chunks);
        String prompt = promptBuilder.build(request.question(), history, context.systemConstraints(), context.pinnedContext(), context.retrievedContext());
        String answer = chatModelService.chat(prompt);
        return new RagAnswerVO(answer, citationService.build(context.allChunks()));
    }

    @Override
    /**
     * 执行流式问答，边生成模型输出边返回引用信息。
     */
    public RagStreamResult askStream(RagAskRequest request, List<ChatHistoryMessage> history, Consumer<String> tokenConsumer) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        RagContext context = buildContext(request.knowledgeBaseId(), chunks);
        String prompt = promptBuilder.build(request.question(), history, context.systemConstraints(), context.pinnedContext(), context.retrievedContext());
        chatModelService.streamChat(prompt, tokenConsumer);
        return new RagStreamResult(citationService.build(context.allChunks()));
    }

    /**
     * 使用所有查询变体做向量召回，并按 chunkId 合并重复候选。
     */
    private List<RagSearchItemVO> vectorCandidates(RagSearchRequest request, QueryRewriteResult rewrite, int candidateTopK) {
        double minScore = request.minScore() == null ? ragProperties.minScore() : request.minScore();
        return rewrite.queryVariants().stream()
                .flatMap(query -> {
                    try {
                        List<Double> queryEmbedding = embeddingService.embedText(query);
                        List<VectorSearchResult> vectorResults = vectorStoreService.searchSimilarChunks(request.knowledgeBaseId(), queryEmbedding, candidateTopK, minScore);
                        return hydrate(vectorResults).stream();
                    } catch (RuntimeException ex) {
                        // 向量召回失败不能影响关键词召回，保留混合检索链路的可用性。
                        log.warn("Vector retrieval failed for query variant '{}', continue with hybrid keyword candidates: {}", query, ex.getMessage());
                        return List.<RagSearchItemVO>of().stream();
                    }
                })
                .collect(Collectors.toMap(RagSearchItemVO::chunkId, item -> item, (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(RagSearchItemVO::score).reversed())
                .limit(candidateTopK)
                .toList();
    }

    /**
     * 将向量库返回的 chunkId 批量补齐为前端可展示的检索项。
     */
    private List<RagSearchItemVO> hydrate(List<VectorSearchResult> vectorResults) {
        if (vectorResults.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scores = vectorResults.stream().collect(Collectors.toMap(VectorSearchResult::chunkId, VectorSearchResult::score, (a, b) -> a));
        List<DocumentChunk> chunks = documentChunkMapper.selectBatchIds(new ArrayList<>(scores.keySet()));
        Map<Long, Document> documents = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .distinct()
                .map(documentMapper::selectById)
                .filter(document -> document != null)
                .collect(Collectors.toMap(Document::getId, document -> document));
        return chunks.stream()
                .map(chunk -> {
                    Document document = documents.get(chunk.getDocumentId());
                    String name = document == null ? "未知文档" : document.getOriginalFileName();
                    return new RagSearchItemVO(chunk.getDocumentId(), name, chunk.getId(), chunk.getChunkIndex(), chunk.getContent(), scores.getOrDefault(chunk.getId(), 0.0));
                })
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
    }

    @SafeVarargs
    /**
     * 合并多路候选结果，同一切片保留分数最高的一条。
     */
    private final List<RagSearchItemVO> mergeCandidates(List<RagSearchItemVO>... groups) {
        return List.of(groups).stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(RagSearchItemVO::chunkId, item -> item, (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .toList();
    }

    /**
     * 构建最终喂给模型的上下文，固定约束优先于普通召回内容。
     */
    private RagContext buildContext(Long knowledgeBaseId, List<RagSearchItemVO> retrievedContext) {
        List<RagSearchItemVO> systemConstraints = fixedContext(knowledgeBaseId, DocumentConstraintLevel.SYSTEM, ragProperties.systemConstraintMaxChunks());
        List<RagSearchItemVO> pinnedContext = fixedContext(knowledgeBaseId, DocumentConstraintLevel.PINNED, ragProperties.pinnedMaxChunks());
        Set<Long> fixedChunkIds = new LinkedHashSet<>();
        systemConstraints.forEach(item -> fixedChunkIds.add(item.chunkId()));
        pinnedContext.forEach(item -> fixedChunkIds.add(item.chunkId()));
        // 普通召回中如果已经包含系统约束或固定资料，需要去重，避免模型上下文重复放大同一段内容。
        List<RagSearchItemVO> normalContext = retrievedContext.stream()
                .filter(item -> !fixedChunkIds.contains(item.chunkId()))
                .toList();
        List<RagSearchItemVO> allChunks = dedupe(systemConstraints, pinnedContext, normalContext);
        return new RagContext(systemConstraints, pinnedContext, normalContext, allChunks);
    }

    /**
     * 查询系统约束或固定资料文档，按优先级稳定注入问答上下文。
     */
    private List<RagSearchItemVO> fixedContext(Long knowledgeBaseId, DocumentConstraintLevel level, int limit) {
        if (knowledgeBaseId == null || limit <= 0) {
            return List.of();
        }
        List<Document> documents = documentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .eq(Document::getStatus, DocumentStatus.INDEXED)
                .eq(Document::getConstraintLevel, level)
                .orderByAsc(Document::getConstraintPriority)
                .orderByAsc(Document::getId));
        if (documents.isEmpty()) {
            return List.of();
        }
        List<RagSearchItemVO> results = new ArrayList<>();
        for (Document document : documents) {
            List<DocumentChunk> chunks = documentChunkMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getDocumentId, document.getId())
                    .orderByAsc(DocumentChunk::getChunkIndex));
            for (DocumentChunk chunk : chunks) {
                results.add(new RagSearchItemVO(document.getId(), document.getOriginalFileName(), chunk.getId(), chunk.getChunkIndex(), chunk.getContent(), 1.0));
                if (results.size() >= limit) {
                    return results;
                }
            }
        }
        return results;
    }

    @SafeVarargs
    /**
     * 按 chunkId 对多个上下文分组去重，保持传入分组的优先顺序。
     */
    private final List<RagSearchItemVO> dedupe(List<RagSearchItemVO>... groups) {
        Set<Long> seen = new LinkedHashSet<>();
        List<RagSearchItemVO> result = new ArrayList<>();
        for (List<RagSearchItemVO> group : groups) {
            for (RagSearchItemVO item : group) {
                if (seen.add(item.chunkId())) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private record RagContext(
            List<RagSearchItemVO> systemConstraints,
            List<RagSearchItemVO> pinnedContext,
            List<RagSearchItemVO> retrievedContext,
            List<RagSearchItemVO> allChunks
    ) {
    }
}
