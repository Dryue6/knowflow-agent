package com.example.knowledgeagent.document.service.impl;

import com.example.knowledgeagent.common.api.ErrorCode;
import com.example.knowledgeagent.common.exception.BusinessException;
import com.example.knowledgeagent.document.service.VectorChunkInput;
import com.example.knowledgeagent.document.service.VectorSearchResult;
import com.example.knowledgeagent.document.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "type", havingValue = "pgvector")
/**
 * 定义 PgVectorStoreService 组件，承载对应模块的业务职责。
 * <p>
 * 文档服务连接串使用 currentSchema=document，而 pgvector 扩展安装在 public schema。
 * SQL 中显式使用 CAST(? AS public.vector) 与 OPERATOR(public.<=>)，避免容器环境下
 * currentSchema 导致 pgvector 类型或距离操作符不可见。
 */
public class PgVectorStoreService implements VectorStoreService {
    private final JdbcTemplate jdbcTemplate;

    /**
     * 将文档切片向量写入 PostgreSQL pgvector 表。
     * <p>
     * document_vector 是独立向量表，保留 chunkId/documentId/knowledgeBaseId，
     * 这样检索后可以快速回到业务表构建引用来源。
     */
    @Override
    public List<String> upsertChunks(List<VectorChunkInput> chunks) {
        try {
            return chunks.stream().map(chunk -> {
                String vectorId = UUID.randomUUID().toString().replace("-", "");
                jdbcTemplate.update("""
                                INSERT INTO document_vector(vector_id, knowledge_base_id, document_id, chunk_id, chunk_index, content, embedding, metadata_json)
                                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS public.vector), ?::jsonb)
                                ON CONFLICT (vector_id) DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding, metadata_json = EXCLUDED.metadata_json
                                """,
                        vectorId, chunk.knowledgeBaseId(), chunk.documentId(), chunk.chunkId(), chunk.chunkIndex(),
                        chunk.content(), toVectorLiteral(chunk.embedding()), chunk.metadataJson());
                return vectorId;
            }).toList();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VECTOR_ERROR, "写入 PgVector 失败: " + ex.getMessage());
        }
    }

    /**
     * 使用 pgvector 的 cosine distance 检索相似切片。
     * <p>
     * 表达式 `1 - (embedding <=> query)` 将 cosine distance 转成相似度分数，
     * 前端展示和 minScore 过滤都使用这个相似度。
     */
    @Override
    public List<VectorSearchResult> searchSimilarChunks(Long knowledgeBaseId, List<Double> queryEmbedding, int topK, double minScore) {
        try {
            String vector = toVectorLiteral(queryEmbedding);
            if (knowledgeBaseId == null) {
                return jdbcTemplate.query("""
                                SELECT vector_id, knowledge_base_id, document_id, chunk_id, chunk_index, content,
                                       1 - (embedding OPERATOR(public.<=>) CAST(? AS public.vector)) AS score
                                FROM document_vector
                                ORDER BY embedding OPERATOR(public.<=>) CAST(? AS public.vector)
                                LIMIT ?
                                """,
                        (rs, rowNum) -> new VectorSearchResult(rs.getString("vector_id"), rs.getLong("knowledge_base_id"),
                                rs.getLong("document_id"), rs.getLong("chunk_id"), rs.getInt("chunk_index"),
                                rs.getString("content"), rs.getDouble("score")),
                        vector, vector, topK).stream()
                        // minScore 在 Java 层过滤，避免 pgvector 表达式与 JDBC 阈值参数组合时出现运行期 SQL 解析差异。
                        .filter(item -> item.score() >= minScore)
                        .toList();
            }
            return jdbcTemplate.query("""
                            SELECT vector_id, knowledge_base_id, document_id, chunk_id, chunk_index, content,
                                   1 - (embedding OPERATOR(public.<=>) CAST(? AS public.vector)) AS score
                            FROM document_vector
                            WHERE knowledge_base_id = ?
                            ORDER BY embedding OPERATOR(public.<=>) CAST(? AS public.vector)
                            LIMIT ?
                            """,
                    (rs, rowNum) -> new VectorSearchResult(rs.getString("vector_id"), rs.getLong("knowledge_base_id"),
                            rs.getLong("document_id"), rs.getLong("chunk_id"), rs.getInt("chunk_index"),
                            rs.getString("content"), rs.getDouble("score")),
                    vector, knowledgeBaseId, vector, topK).stream()
                    // minScore 在 Java 层过滤，保持 SQL 只负责向量排序和候选截断。
                    .filter(item -> item.score() >= minScore)
                    .toList();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VECTOR_ERROR, "检索 PgVector 失败: " + ex.getMessage());
        }
    }

    /**
     * 删除指定文档在 pgvector 表中的全部向量。
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update("DELETE FROM document_vector WHERE document_id = ?", documentId);
    }

    /**
     * 删除指定知识库在 pgvector 表中的全部向量。
     */
    @Override
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        jdbcTemplate.update("DELETE FROM document_vector WHERE knowledge_base_id = ?", knowledgeBaseId);
    }

    /**
     * 将 Java List 向量转换为 pgvector 字面量格式，例如 `[0.1,0.2]`。
     */
    private String toVectorLiteral(List<Double> vector) {
        return "[" + String.join(",", vector.stream().map(String::valueOf).toList()) + "]";
    }
}
