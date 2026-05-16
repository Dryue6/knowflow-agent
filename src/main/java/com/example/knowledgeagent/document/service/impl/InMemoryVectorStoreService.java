package com.example.knowledgeagent.document.service.impl;

import com.example.knowledgeagent.document.service.VectorChunkInput;
import com.example.knowledgeagent.document.service.VectorSearchResult;
import com.example.knowledgeagent.document.service.VectorStoreService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "vector", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStoreService implements VectorStoreService {
    private final Map<String, StoredVector> vectors = new ConcurrentHashMap<>();

    /**
     * 将切片向量写入内存 Map，并返回生成的 vectorId。
     */
    @Override
    public List<String> upsertChunks(List<VectorChunkInput> chunks) {
        return chunks.stream().map(chunk -> {
            String vectorId = UUID.randomUUID().toString().replace("-", "");
            vectors.put(vectorId, new StoredVector(vectorId, chunk));
            return vectorId;
        }).toList();
    }

    /**
     * 在内存中按余弦相似度检索相关切片。
     * <p>
     * 该实现主要用于开发和测试，不依赖外部 pgvector 服务；生产环境建议使用 PgVectorStoreService。
     */
    @Override
    public List<VectorSearchResult> searchSimilarChunks(Long knowledgeBaseId, List<Double> queryEmbedding, int topK, double minScore) {
        return vectors.values().stream()
                .filter(item -> knowledgeBaseId == null || knowledgeBaseId.equals(item.input.knowledgeBaseId()))
                .map(item -> toResult(item, cosine(queryEmbedding, item.input.embedding())))
                .filter(result -> result.score() >= minScore)
                .sorted(Comparator.comparing(VectorSearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * 删除指定文档的所有内存向量。
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        vectors.entrySet().removeIf(entry -> documentId.equals(entry.getValue().input.documentId()));
    }

    /**
     * 删除指定知识库的所有内存向量。
     */
    @Override
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        vectors.entrySet().removeIf(entry -> knowledgeBaseId.equals(entry.getValue().input.knowledgeBaseId()));
    }

    /**
     * 将内部存储对象转换为统一向量检索结果。
     */
    private VectorSearchResult toResult(StoredVector item, double score) {
        VectorChunkInput input = item.input;
        return new VectorSearchResult(item.vectorId, input.knowledgeBaseId(), input.documentId(), input.chunkId(), input.chunkIndex(), input.content(), score);
    }

    /**
     * 计算余弦相似度。
     */
    private double cosine(List<Double> left, List<Double> right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record StoredVector(String vectorId, VectorChunkInput input) {
    }
}
