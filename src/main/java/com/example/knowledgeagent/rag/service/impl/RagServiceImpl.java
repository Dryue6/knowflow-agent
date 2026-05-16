package com.example.knowledgeagent.rag.service.impl;

import com.example.knowledgeagent.config.RagProperties;
import com.example.knowledgeagent.document.embedding.EmbeddingService;
import com.example.knowledgeagent.document.entity.Document;
import com.example.knowledgeagent.document.entity.DocumentChunk;
import com.example.knowledgeagent.document.mapper.DocumentChunkMapper;
import com.example.knowledgeagent.document.mapper.DocumentMapper;
import com.example.knowledgeagent.document.service.VectorSearchResult;
import com.example.knowledgeagent.document.service.VectorStoreService;
import com.example.knowledgeagent.rag.dto.ChatHistoryMessage;
import com.example.knowledgeagent.rag.dto.RagAskRequest;
import com.example.knowledgeagent.rag.dto.RagSearchRequest;
import com.example.knowledgeagent.rag.prompt.RagPromptBuilder;
import com.example.knowledgeagent.rag.service.ChatModelService;
import com.example.knowledgeagent.rag.service.RagService;
import com.example.knowledgeagent.rag.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final RagProperties ragProperties;
    private final RagPromptBuilder promptBuilder;
    private final ChatModelService chatModelService;

    /**
     * 根据用户 query 执行向量召回，并把向量库结果补齐为前端可展示的文档切片信息。
     */
    @Override
    public RagSearchResponseVO retrieve(RagSearchRequest request) {
        int topK = request.topK() == null ? ragProperties.topK() : request.topK();
        double minScore = request.minScore() == null ? ragProperties.minScore() : request.minScore();
        List<Double> queryEmbedding = embeddingService.embedText(request.query());
        List<VectorSearchResult> vectorResults = vectorStoreService.searchSimilarChunks(request.knowledgeBaseId(), queryEmbedding, topK, minScore);
        return new RagSearchResponseVO(request.query(), hydrate(vectorResults));
    }

    /**
     * 非流式 RAG 问答。
     * <p>
     * 先召回知识库切片，再拼装 Prompt，最后调用 Chat Model 生成完整答案。
     * citations 基于召回切片生成，和模型回答分离，避免模型遗漏或编造来源。
     */
    @Override
    public RagAnswerVO ask(RagAskRequest request, List<ChatHistoryMessage> history) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        String prompt = promptBuilder.build(request.question(), history, chunks);
        String answer = chatModelService.chat(prompt);
        return new RagAnswerVO(answer, buildCitations(chunks));
    }

    /**
     * 流式 RAG 问答。
     * <p>
     * Prompt 和引用来源构建逻辑与非流式一致，区别是模型输出通过 tokenConsumer 增量推送给调用方。
     * 返回值只包含引用来源，最终答案由调用方在消费 token 时自行累积。
     */
    @Override
    public RagStreamResult askStream(RagAskRequest request, List<ChatHistoryMessage> history, Consumer<String> tokenConsumer) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        String prompt = promptBuilder.build(request.question(), history, chunks);
        chatModelService.streamChat(prompt, tokenConsumer);
        return new RagStreamResult(buildCitations(chunks));
    }

    /**
     * 将向量库召回结果转换为 RAG 展示对象。
     * <p>
     * 向量库只保存 chunkId、documentId、score 等检索必要信息；这里批量回查 chunk 和 document，
     * 补齐文档名、原始内容，并按分数倒序返回。
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

    /**
     * 根据召回切片构建答案引用来源。
     */
    private List<CitationVO> buildCitations(List<RagSearchItemVO> chunks) {
        return chunks.stream()
                .map(item -> new CitationVO(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(), preview(item.content()), item.score()))
                .toList();
    }

    /**
     * 生成引用预览文本，避免前端引用卡片一次性展示过长内容。
     */
    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 160 ? content : content.substring(0, 160) + "...";
    }
}
