-- 将文档向量表迁移到 qwen3-embedding:0.6b 的原生 1024 维。
-- 该脚本可重复执行：新库如果已经是 vector(1024)，ALTER 仍保持同一维度。
DELETE FROM document.document_vector v
USING document.document d
WHERE v.document_id = d.id
  AND d.status = 'DELETED';

DELETE FROM document.document_vector v
WHERE NOT EXISTS (
    SELECT 1
    FROM document.document d
    WHERE d.id = v.document_id
);

DROP INDEX IF EXISTS document.idx_document_schema_vector_embedding;

ALTER TABLE document.document_vector
    ALTER COLUMN embedding TYPE public.vector(1024)
    USING public.subvector(embedding, 1, 1024)::public.vector(1024);

CREATE INDEX IF NOT EXISTS idx_document_schema_vector_embedding
    ON document.document_vector
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
