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

    @Override
    public RagSearchResponseVO retrieve(RagSearchRequest request) {
        int topK = request.topK() == null ? ragProperties.topK() : request.topK();
        double minScore = request.minScore() == null ? ragProperties.minScore() : request.minScore();
        List<Double> queryEmbedding = embeddingService.embedText(request.query());
        List<VectorSearchResult> vectorResults = vectorStoreService.searchSimilarChunks(request.knowledgeBaseId(), queryEmbedding, topK, minScore);
        return new RagSearchResponseVO(request.query(), hydrate(vectorResults));
    }

    @Override
    public RagAnswerVO ask(RagAskRequest request, List<ChatHistoryMessage> history) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        String prompt = promptBuilder.build(request.question(), history, chunks);
        String answer = chatModelService.chat(prompt);
        return new RagAnswerVO(answer, buildCitations(chunks));
    }

    @Override
    public RagStreamResult askStream(RagAskRequest request, List<ChatHistoryMessage> history, Consumer<String> tokenConsumer) {
        List<RagSearchItemVO> chunks = retrieve(new RagSearchRequest(request.knowledgeBaseId(), request.question(), ragProperties.maxContextChunks(), ragProperties.minScore())).chunks();
        String prompt = promptBuilder.build(request.question(), history, chunks);
        chatModelService.streamChat(prompt, tokenConsumer);
        return new RagStreamResult(buildCitations(chunks));
    }

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

    private List<CitationVO> buildCitations(List<RagSearchItemVO> chunks) {
        return chunks.stream()
                .map(item -> new CitationVO(item.documentId(), item.documentName(), item.chunkId(), item.chunkIndex(), preview(item.content()), item.score()))
                .toList();
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 160 ? content : content.substring(0, 160) + "...";
    }
}
