package com.example.knowflow.document.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowledgeagent.common.api.ApiResult;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.embedding.EmbeddingService;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.enums.DocumentConstraintLevel;
import com.example.knowledgeagent.document.enums.DocumentStatus;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.service.VectorSearchResult;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowflow.contract.dto.DocumentAdjacentContextCommand;
import com.example.knowflow.contract.dto.DocumentFixedContextCommand;
import com.example.knowflow.contract.dto.DocumentKeywordSearchCommand;
import com.example.knowflow.contract.dto.DocumentVectorSearchCommand;
import com.example.knowflow.contract.dto.RagSearchItem;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * document-service 提供给 RAG 的内部检索接口，集中持有文档表和向量库访问权限。
 */
@RestController
@RequestMapping("/internal/documents")
public class InternalDocumentSearchController {
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /**
     * 注入文档检索所需的本服务组件。
     */
    public InternalDocumentSearchController(DocumentMapper documentMapper,
                                            DocumentChunkMapper documentChunkMapper,
                                            EmbeddingService embeddingService,
                                            VectorStoreService vectorStoreService) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 使用 queryVariants 分别生成 embedding 并执行向量召回，同一切片只保留最高分。
     */
    @PostMapping("/search/vector")
    public ApiResult<List<RagSearchItem>> vectorSearch(@RequestBody DocumentVectorSearchCommand request) {
        int topK = request.topK() == null ? 10 : request.topK();
        double minScore = request.minScore() == null ? 0.0 : request.minScore();
        List<String> variants = request.queryVariants() == null ? List.of() : request.queryVariants();
        List<RagSearchItem> results = variants.stream()
                .flatMap(query -> {
                    List<Double> embedding = embeddingService.embedText(query);
                    List<VectorSearchResult> vectorResults = vectorStoreService.searchSimilarChunks(request.knowledgeBaseId(), embedding, topK, minScore);
                    return hydrate(vectorResults).stream();
                })
                .collect(Collectors.toMap(RagSearchItem::chunkId, item -> item, (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(RagSearchItem::score).reversed())
                .limit(topK)
                .toList();
        return ApiResult.ok(results);
    }

    /**
     * 执行关键词召回，并补充相邻切片作为候选上下文。
     */
    @PostMapping("/search/keyword")
    public ApiResult<List<RagSearchItem>> keywordSearch(@RequestBody DocumentKeywordSearchCommand request) {
        int topK = request.topK() == null ? 10 : request.topK();
        double minScore = request.minScore() == null ? 0.0 : request.minScore();
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(request.knowledgeBaseId() != null, DocumentChunk::getKnowledgeBaseId, request.knowledgeBaseId()));
        if (chunks.isEmpty()) {
            return ApiResult.ok(List.of());
        }
        Map<Long, Document> documents = documentsById(chunks);
        Map<String, DocumentChunk> byDocumentAndIndex = chunks.stream()
                .collect(Collectors.toMap(chunk -> key(chunk.getDocumentId(), chunk.getChunkIndex()), chunk -> chunk, (a, b) -> a));
        List<RagSearchItem> directMatches = chunks.stream()
                .map(chunk -> keywordResult(chunk, documents.get(chunk.getDocumentId()), request))
                .filter(item -> item.score() >= minScore)
                .sorted(Comparator.comparingDouble(RagSearchItem::score).reversed())
                .limit(topK)
                .toList();
        List<RagSearchItem> adjacentMatches = directMatches.stream()
                .flatMap(item -> List.of(item.chunkIndex() - 1, item.chunkIndex() + 1).stream()
                        .map(index -> byDocumentAndIndex.get(key(item.documentId(), index)))
                        .filter(Objects::nonNull)
                        .filter(chunk -> !chunk.getId().equals(item.chunkId()))
                        .map(chunk -> adjacentResult(chunk, documents.get(chunk.getDocumentId()), item.score())))
                .filter(item -> item.score() >= minScore)
                .toList();
        List<RagSearchItem> merged = List.of(directMatches, adjacentMatches).stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(RagSearchItem::chunkId, item -> item, (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(RagSearchItem::score).reversed())
                .limit(topK)
                .toList();
        return ApiResult.ok(merged);
    }

    /**
     * 查询系统约束或置顶文档切片，保证 RAG 固定上下文仍由 document-service 统一读取。
     */
    @PostMapping("/context/fixed")
    public ApiResult<List<RagSearchItem>> fixedContext(@RequestBody DocumentFixedContextCommand request) {
        if (request.knowledgeBaseId() == null || request.limit() == null || request.limit() <= 0) {
            return ApiResult.ok(List.of());
        }
        DocumentConstraintLevel level = parseLevel(request.constraintLevel());
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, request.knowledgeBaseId())
                .eq(Document::getStatus, DocumentStatus.INDEXED)
                .eq(Document::getConstraintLevel, level)
                .orderByAsc(Document::getConstraintPriority)
                .orderByAsc(Document::getId));
        List<RagSearchItem> results = new ArrayList<>();
        for (Document document : documents) {
            List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getDocumentId, document.getId())
                    .orderByAsc(DocumentChunk::getChunkIndex));
            for (DocumentChunk chunk : chunks) {
                results.add(toItem(chunk, document, 1.0));
                if (results.size() >= request.limit()) {
                    return ApiResult.ok(results);
                }
            }
        }
        return ApiResult.ok(results);
    }

    /**
     * 根据已命中的 chunk 位置查询同文档前后相邻 chunk。
     *
     * <p>该接口只做回答上下文扩展，不参与向量排序；返回分数固定低于直接命中，用于标识其补充属性。</p>
     */
    @PostMapping("/context/adjacent")
    public ApiResult<List<RagSearchItem>> adjacentContext(@RequestBody DocumentAdjacentContextCommand request) {
        if (request == null || request.anchors() == null || request.anchors().isEmpty()) {
            return ApiResult.ok(List.of());
        }
        int windowSize = Math.max(1, request.windowSize() == null ? 1 : request.windowSize());
        int limit = Math.max(1, request.limit() == null ? request.anchors().size() * windowSize * 2 : request.limit());
        List<RagSearchItem> results = new ArrayList<>();
        List<Long> seenChunkIds = new ArrayList<>();
        for (DocumentAdjacentContextCommand.Anchor anchor : request.anchors()) {
            if (anchor == null || anchor.documentId() == null || anchor.chunkIndex() == null) {
                continue;
            }
            Document document = documentMapper.selectById(anchor.documentId());
            if (document == null || document.getStatus() != DocumentStatus.INDEXED) {
                continue;
            }
            if (request.knowledgeBaseId() != null && !request.knowledgeBaseId().equals(document.getKnowledgeBaseId())) {
                continue;
            }
            for (int offset = -windowSize; offset <= windowSize; offset++) {
                if (offset == 0) {
                    continue;
                }
                DocumentChunk chunk = adjacentChunk(document, anchor.chunkIndex() + offset);
                if (chunk == null || chunk.getId().equals(anchor.chunkId()) || seenChunkIds.contains(chunk.getId())) {
                    continue;
                }
                seenChunkIds.add(chunk.getId());
                results.add(adjacentResult(chunk, document, 0.8));
                if (results.size() >= limit) {
                    return ApiResult.ok(results);
                }
            }
        }
        return ApiResult.ok(results);
    }

    /**
     * 将向量库命中的 chunkId 补齐为跨服务检索结果。
     */
    private List<RagSearchItem> hydrate(List<VectorSearchResult> vectorResults) {
        if (vectorResults.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scores = vectorResults.stream().collect(Collectors.toMap(VectorSearchResult::chunkId, VectorSearchResult::score, (a, b) -> a));
        List<DocumentChunk> chunks = documentChunkMapper.selectBatchIds(new ArrayList<>(scores.keySet()));
        Map<Long, Document> documents = documentsById(chunks);
        return chunks.stream()
                .map(chunk -> toItem(chunk, documents.get(chunk.getDocumentId()), scores.getOrDefault(chunk.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(RagSearchItem::score).reversed())
                .toList();
    }

    /**
     * 批量查询切片所属文档，避免 RAG 服务直接访问 document 表。
     */
    private Map<Long, Document> documentsById(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .distinct()
                .map(documentMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Document::getId, document -> document));
    }

    /**
     * 根据关键词命中情况计算切片得分。
     */
    private RagSearchItem keywordResult(DocumentChunk chunk, Document document, DocumentKeywordSearchCommand request) {
        String content = normalize(chunk.getContent());
        String documentName = document == null ? "未知文档" : document.getOriginalFileName();
        String title = normalize(documentName + " " + (document == null ? "" : document.getTitle()) + " " + chunk.getSectionTitle());
        double score = 0;
        score += scoreTerms(content, title, request.phraseTerms(), 0.75, 0.5);
        score += scoreTerms(content, title, request.coreTerms(), 0.65, 0.45);
        score += scoreTerms(content, title, request.expandedTerms(), 0.35, 0.25);
        double positionBoost = chunk.getChunkIndex() != null && chunk.getChunkIndex() <= 2 ? 0.05 : 0;
        return toItem(chunk, document, Math.min(1, score + positionBoost));
    }

    /**
     * 计算一组关键词在正文和标题中的命中得分。
     */
    private double scoreTerms(String content, String title, List<String> terms, double contentScore, double titleScore) {
        if (terms == null || terms.isEmpty()) {
            return 0;
        }
        double score = 0;
        for (String value : terms) {
            String term = normalize(value);
            if (!term.isBlank() && content.contains(term)) {
                score += contentScore;
            }
            if (!term.isBlank() && title.contains(term)) {
                score += titleScore;
            }
        }
        return score;
    }

    /**
     * 构造相邻切片候选，分数低于直接命中以体现上下文补充属性。
     */
    private RagSearchItem adjacentResult(DocumentChunk chunk, Document document, double sourceScore) {
        return toItem(chunk, document, Math.min(0.65, sourceScore * 0.75));
    }

    /**
     * 查询单个相邻 chunk。按 documentId + chunkIndex 精确读取，避免把其他文档内容拼入回答上下文。
     */
    private DocumentChunk adjacentChunk(Document document, int chunkIndex) {
        if (chunkIndex < 0) {
            return null;
        }
        return documentChunkMapper.selectOne(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, document.getId())
                .eq(DocumentChunk::getChunkIndex, chunkIndex)
                .last("LIMIT 1"));
    }

    /**
     * 将文档切片转换成跨服务检索 DTO。
     */
    private RagSearchItem toItem(DocumentChunk chunk, Document document, double score) {
        String documentName = document == null ? "未知文档" : document.getOriginalFileName();
        return new RagSearchItem(chunk.getDocumentId(), documentName, chunk.getId(), chunk.getChunkIndex(), chunk.getContent(), score);
    }

    /**
     * 解析固定上下文等级，非法值转成统一业务异常。
     */
    private DocumentConstraintLevel parseLevel(String value) {
        try {
            return DocumentConstraintLevel.valueOf(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文档约束等级");
        }
    }

    /**
     * 生成文档内切片位置 key。
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
