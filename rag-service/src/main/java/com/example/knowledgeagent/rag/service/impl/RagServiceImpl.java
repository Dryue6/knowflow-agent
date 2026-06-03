package com.example.knowledgeagent.rag.service.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.prompt.RagPromptBuilder;
import com.example.knowledgeagent.rag.service.ChatModelService;
import com.example.knowledgeagent.rag.service.QueryRewriteResult;
import com.example.knowledgeagent.rag.service.QueryRewriteService;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.service.RerankService;
import com.example.knowledgeagent.rag.vo.CitationVO;
import com.example.knowledgeagent.rag.vo.RagAnswerVO;
import com.example.knowledgeagent.rag.vo.RagSearchItemVO;
import com.example.knowledgeagent.rag.vo.RagSearchResponseVO;
import com.example.knowledgeagent.rag.vo.RagStreamResult;
import com.example.knowflow.contract.client.DocumentClient;
import com.example.knowflow.contract.dto.DocumentAdjacentContextCommand;
import com.example.knowflow.contract.dto.DocumentFixedContextCommand;
import com.example.knowflow.contract.dto.DocumentKeywordSearchCommand;
import com.example.knowflow.contract.dto.DocumentVectorSearchCommand;
import com.example.knowflow.contract.dto.RagSearchItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.StringUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final DocumentClient documentClient;
    private final QueryRewriteService queryRewriteService;
    private final RerankService rerankService;
    private final RagProperties ragProperties;
    private final RagPromptBuilder promptBuilder;
    private final ChatModelService chatModelService;
    private final LocalFallbackChatService localFallbackChatService;

    @Override
    /**
     * 执行 RAG 检索流程。
     *
     * <p>关键词召回先行，能够快速判断知识库是否存在明显相关资料；向量召回只使用主查询做补充，
     * 避免本地 embedding 对多个改写问法逐条推理导致整体回答变慢。</p>
     */
    public RagSearchResponseVO retrieve(RagSearchRequest request) {
        int topK = request.topK() == null ? ragProperties.topK() : request.topK();
        int candidateTopK = Math.max(topK, ragProperties.candidateTopK());
        QueryRewriteResult rewrite = queryRewriteService.rewrite(request.query());
        List<RagSearchItemVO> keywordCandidates = keywordCandidates(request, rewrite, candidateTopK);
        if (keywordCandidates.isEmpty()) {
            // 没有关键词命中时通常代表知识库缺少相关资料，直接进入本地通用回答分支，
            // 避免无资料问题仍触发本地 embedding 推理导致长时间等待。
            log.info("Keyword retrieval found no candidates, skip vector retrieval and use no-context answer path");
            return new RagSearchResponseVO(request.query(), List.of());
        }
        List<RagSearchItemVO> vectorCandidates = vectorCandidates(request, rewrite, candidateTopK);
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
        String answer = answerWithSelectedModel(prompt, context);
        return new RagAnswerVO(answer, buildAnswerCitations(context));
    }

    @Override
    /**
     * 执行流式问答，边生成模型输出边返回引用信息。
     */
    public RagStreamResult askStream(RagAskRequest request, List<ChatHistoryMessage> history, Consumer<String> tokenConsumer) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        RagContext context = buildContext(request.knowledgeBaseId(), chunks);
        String prompt = promptBuilder.build(request.question(), history, context.systemConstraints(), context.pinnedContext(), context.retrievedContext());
        streamWithSelectedModel(prompt, context, tokenConsumer);
        return new RagStreamResult(buildAnswerCitations(context));
    }

    /**
     * 使用主查询做向量补召回，并按 chunkId 合并重复候选。
     */
    private List<RagSearchItemVO> vectorCandidates(RagSearchRequest request, QueryRewriteResult rewrite, int candidateTopK) {
        double minScore = request.minScore() == null ? ragProperties.minScore() : request.minScore();
        var response = documentClient.vectorSearch(new DocumentVectorSearchCommand(request.knowledgeBaseId(), primaryQueryVariants(request, rewrite), candidateTopK, minScore));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            // 向量召回失败不能影响关键词召回，保留混合检索链路的可用性。
            log.warn("Vector retrieval failed through document-service, continue with keyword candidates: {}", response.message());
            return List.of();
        }
        return toVoList(response.data());
    }

    /**
     * 只保留一个主查询给向量检索，减少本地 embedding 推理次数。
     *
     * <p>查询改写产生的短语词、核心词和扩展词仍用于关键词召回；这里压缩向量查询数量，
     * 是为了在本地 embedding 模型推理时优先保证问答响应速度。</p>
     */
    private List<String> primaryQueryVariants(RagSearchRequest request, QueryRewriteResult rewrite) {
        if (rewrite.queryVariants() != null && !rewrite.queryVariants().isEmpty()) {
            List<String> selectedVariants = rewrite.queryVariants().stream()
                    .filter(StringUtils::hasText)
                    .limit(5)
                    .toList();
            if (!selectedVariants.isEmpty()) {
                // 输出实际参与向量召回的查询变体，便于排查多路 embedding 召回效果和耗时。
                for (int i = 0; i < selectedVariants.size(); i++) {
                    log.info("当前查询变体[{}]: {}", i + 1, selectedVariants.get(i));
                }
                return selectedVariants;
            }
        }
        log.info("Vector retrieval query variant fallback: {}", request.query());
        return List.of(request.query());
    }

    /**
     * 通过 document-service 执行关键词召回，RAG 服务不再直接访问文档表。
     */
    private List<RagSearchItemVO> keywordCandidates(RagSearchRequest request, QueryRewriteResult rewrite, int candidateTopK) {
        var response = documentClient.keywordSearch(new DocumentKeywordSearchCommand(
                request.knowledgeBaseId(),
                rewrite.phraseTerms(),
                rewrite.coreTerms(),
                rewrite.expandedTerms(),
                candidateTopK,
                ragProperties.keywordMinScore()));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.message());
        }
        return toVoList(response.data());
    }

    /**
     * 将 document-service 返回的跨服务 DTO 转成 RAG 内部 VO。
     */
    private List<RagSearchItemVO> toVoList(List<RagSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new RagSearchItemVO(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(), item.content(), item.score()))
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
        List<RagSearchItemVO> systemConstraints = fixedContext(knowledgeBaseId, "SYSTEM", ragProperties.systemConstraintMaxChunks());
        List<RagSearchItemVO> pinnedContext = fixedContext(knowledgeBaseId, "PINNED", ragProperties.pinnedMaxChunks());
        Set<Long> fixedChunkIds = new LinkedHashSet<>();
        systemConstraints.forEach(item -> fixedChunkIds.add(item.chunkId()));
        pinnedContext.forEach(item -> fixedChunkIds.add(item.chunkId()));
        // 普通召回中如果已经包含系统约束或固定资料，需要去重，避免模型上下文重复放大同一段内容。
        List<RagSearchItemVO> expandedRetrievedContext = expandRetrievedContext(knowledgeBaseId, retrievedContext);
        List<RagSearchItemVO> normalContext = expandedRetrievedContext.stream()
                .filter(item -> !fixedChunkIds.contains(item.chunkId()))
                .toList();
        List<RagSearchItemVO> allChunks = dedupe(systemConstraints, pinnedContext, normalContext);
        // 系统约束只限制模型行为，不代表当前问题找到了可引用资料；
        // 只有固定资料或普通检索资料命中时，才向前端返回 citations。
        boolean hasAnswerContext = !pinnedContext.isEmpty() || !normalContext.isEmpty();
        return new RagContext(systemConstraints, pinnedContext, normalContext, allChunks, hasAnswerContext);
    }

    /**
     * 查询系统约束或固定资料文档，按优先级稳定注入问答上下文。
     */
    private List<RagSearchItemVO> fixedContext(Long knowledgeBaseId, String level, int limit) {
        if (knowledgeBaseId == null || limit <= 0) {
            return List.of();
        }
        var response = documentClient.fixedContext(new DocumentFixedContextCommand(knowledgeBaseId, level, limit));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.message());
        }
        return toVoList(response.data());
    }

    /**
     * 在最终回答上下文阶段补齐命中 chunk 的前后相邻片段。
     *
     * <p>向量召回仍只负责找相关片段；这里默认各取 1 个相邻 chunk，解决短 chunk 或 OCR chunk
     * 命中后缺少标题、说明文字和上下文的问题。扩展失败时降级为原始命中结果，避免影响问答可用性。</p>
     */
    private List<RagSearchItemVO> expandRetrievedContext(Long knowledgeBaseId, List<RagSearchItemVO> retrievedContext) {
        if (knowledgeBaseId == null || retrievedContext == null || retrievedContext.isEmpty()) {
            return retrievedContext == null ? List.of() : retrievedContext;
        }
        List<DocumentAdjacentContextCommand.Anchor> anchors = retrievedContext.stream()
                .filter(item -> item.documentId() != null && item.chunkIndex() != null)
                .map(item -> new DocumentAdjacentContextCommand.Anchor(item.documentId(), item.chunkIndex(), item.chunkId()))
                .toList();
        if (anchors.isEmpty()) {
            return retrievedContext;
        }
        var response = documentClient.adjacentContext(new DocumentAdjacentContextCommand(
                knowledgeBaseId,
                anchors,
                1,
                Math.max(2, anchors.size() * 2)));
        if (!ErrorCode.SUCCESS.getCode().equals(response.code())) {
            log.warn("Adjacent context retrieval failed, continue with direct hits: {}", response.message());
            return retrievedContext;
        }
        List<RagSearchItemVO> adjacentContext = toVoList(response.data());
        List<RagSearchItemVO> expanded = interleaveAdjacentContext(retrievedContext, adjacentContext);
        log.info("RAG 上下文扩展完成，directHits={}, adjacentHits={}, expandedChunks={}, ocrDirectHits={}",
                retrievedContext.size(), adjacentContext.size(), expanded.size(), ocrHitCount(retrievedContext));
        for (RagSearchItemVO item : retrievedContext) {
            log.info("RAG 命中切片，documentId={}, chunkIndex={}, ocr={}, expandedAdjacent={}",
                    item.documentId(), item.chunkIndex(), isDocxOcrChunk(item), hasAdjacent(item, adjacentContext));
        }
        return expanded;
    }

    /**
     * 按“前文 -> 命中 -> 后文”的顺序组织上下文，保证模型阅读顺序尽量接近原文。
     */
    private List<RagSearchItemVO> interleaveAdjacentContext(List<RagSearchItemVO> directHits, List<RagSearchItemVO> adjacentContext) {
        Map<String, RagSearchItemVO> adjacentByPosition = new HashMap<>();
        for (RagSearchItemVO item : adjacentContext) {
            adjacentByPosition.put(positionKey(item.documentId(), item.chunkIndex()), item);
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<RagSearchItemVO> result = new ArrayList<>();
        for (RagSearchItemVO hit : directHits) {
            addIfPresent(result, seen, adjacentByPosition.get(positionKey(hit.documentId(), hit.chunkIndex() == null ? null : hit.chunkIndex() - 1)));
            addIfPresent(result, seen, hit);
            addIfPresent(result, seen, adjacentByPosition.get(positionKey(hit.documentId(), hit.chunkIndex() == null ? null : hit.chunkIndex() + 1)));
        }
        for (RagSearchItemVO item : adjacentContext) {
            addIfPresent(result, seen, item);
        }
        return result;
    }

    /**
     * 对 chunkId 去重追加上下文，避免关键词召回自带相邻片段时重复进入提示词。
     */
    private void addIfPresent(List<RagSearchItemVO> result, Set<Long> seen, RagSearchItemVO item) {
        if (item != null && item.chunkId() != null && seen.add(item.chunkId())) {
            result.add(item);
        }
    }

    /**
     * 判断命中切片是否已经成功补齐前后相邻 chunk，用于检索日志排障。
     */
    private boolean hasAdjacent(RagSearchItemVO hit, List<RagSearchItemVO> adjacentContext) {
        if (hit.chunkIndex() == null) {
            return false;
        }
        String previous = positionKey(hit.documentId(), hit.chunkIndex() - 1);
        String next = positionKey(hit.documentId(), hit.chunkIndex() + 1);
        return adjacentContext.stream()
                .map(item -> positionKey(item.documentId(), item.chunkIndex()))
                .anyMatch(key -> key.equals(previous) || key.equals(next));
    }

    /**
     * 统计直接命中的 DOCX OCR chunk 数量，方便判断图片文字召回是否需要相邻上下文辅助。
     */
    private long ocrHitCount(List<RagSearchItemVO> chunks) {
        return chunks.stream().filter(this::isDocxOcrChunk).count();
    }

    /**
     * DOCX OCR chunk 使用稳定前缀写入正文，无需跨服务传 metadata 即可识别。
     */
    private boolean isDocxOcrChunk(RagSearchItemVO item) {
        return item != null && item.content() != null && item.content().contains("【图片OCR DOCX");
    }

    /**
     * 生成文档内 chunk 位置 key。
     */
    private String positionKey(Long documentId, Integer chunkIndex) {
        return documentId + ":" + chunkIndex;
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

    /**
     * 基于召回切片生成引用来源，详细页码和段落后续可继续由 document-service 提供富化接口。
     */
    private List<CitationVO> buildAnswerCitations(RagContext context) {
        if (!context.hasAnswerContext()) {
            return List.of();
        }
        return buildCitations(context.allChunks());
    }

    /**
     * 根据是否命中知识库资料选择回答模型。
     *
     * <p>命中知识库时走配置的 Chat Model（当前部署为 DeepSeek API），保证带资料回答质量；
     * 未命中知识库时走本地 qwen2.5，满足“没有相关文档也要给出通用回答”的产品要求。</p>
     */
    private String answerWithSelectedModel(String prompt, RagContext context) {
        if (!context.hasAnswerContext()) {
            log.info("No answer context found in knowledge base, use local fallback chat model");
            return localFallbackChatService.chat(prompt);
        }
        return chatModelService.chat(prompt);
    }

    /**
     * 流式问答沿用同一套模型选择规则，保持 SSE 和非流式接口行为一致。
     */
    private void streamWithSelectedModel(String prompt, RagContext context, Consumer<String> tokenConsumer) {
        if (!context.hasAnswerContext()) {
            log.info("No answer context found in knowledge base, stream with local fallback chat model");
            localFallbackChatService.streamChat(prompt, tokenConsumer);
            return;
        }
        chatModelService.streamChat(prompt, tokenConsumer);
    }

    /**
     * 基于召回切片生成引用来源，详细页码和段落后续可继续由 document-service 提供富化接口。
     */
    private List<CitationVO> buildCitations(List<RagSearchItemVO> chunks) {
        return chunks.stream()
                .map(item -> new CitationVO(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(),
                        preview(item.content()), item.score(), null, null, null, null))
                .toList();
    }

    /**
     * 截取引用预览文本，避免聊天响应携带过长上下文。
     */
    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    private record RagContext(
            List<RagSearchItemVO> systemConstraints,
            List<RagSearchItemVO> pinnedContext,
            List<RagSearchItemVO> retrievedContext,
            List<RagSearchItemVO> allChunks,
            boolean hasAnswerContext
    ) {
    }
}
