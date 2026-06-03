BEGIN;

DO $$
DECLARE
    schema_name TEXT;
BEGIN
    -- 为已存在的 Seata undo_log 表补齐 id 主键，兼容 Seata PostgreSQL 清理 SQL。
    FOREACH schema_name IN ARRAY ARRAY['auth', 'knowledge', 'document', 'chat']
    LOOP
        EXECUTE format('ALTER TABLE %I.undo_log ADD COLUMN IF NOT EXISTS id BIGSERIAL', schema_name);

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = schema_name
              AND t.relname = 'undo_log'
              AND c.contype = 'p'
        ) THEN
            EXECUTE format('ALTER TABLE %I.undo_log ADD PRIMARY KEY (id)', schema_name);
        END IF;
    END LOOP;
END $$;

INSERT INTO auth.user_account (
    id,
    username,
    password_hash,
    display_name,
    status,
    created_at,
    updated_at
)
SELECT
    id,
    username,
    password_hash,
    display_name,
    status,
    created_at,
    updated_at
FROM public.user_account
ON CONFLICT DO NOTHING;

INSERT INTO knowledge.knowledge_base (
    id,
    name,
    description,
    status,
    document_count,
    chunk_count,
    created_at,
    updated_at
)
SELECT
    id,
    name,
    description,
    status,
    document_count,
    chunk_count,
    created_at,
    updated_at
FROM public.knowledge_base
ON CONFLICT DO NOTHING;

INSERT INTO document.document (
    id,
    knowledge_base_id,
    file_name,
    original_file_name,
    file_type,
    file_size,
    file_path,
    title,
    status,
    error_message,
    chunk_count,
    constraint_level,
    constraint_priority,
    created_at,
    updated_at
)
SELECT
    id,
    knowledge_base_id,
    file_name,
    original_file_name,
    file_type,
    file_size,
    file_path,
    title,
    status,
    error_message,
    chunk_count,
    COALESCE(constraint_level, 'NORMAL'),
    COALESCE(constraint_priority, 100),
    created_at,
    updated_at
FROM public.document
ON CONFLICT DO NOTHING;

INSERT INTO document.document_chunk (
    id,
    knowledge_base_id,
    document_id,
    chunk_index,
    content,
    content_hash,
    token_count,
    vector_id,
    page_number,
    section_title,
    paragraph_index,
    location_text,
    metadata_json,
    created_at,
    updated_at
)
SELECT
    id,
    knowledge_base_id,
    document_id,
    chunk_index,
    content,
    content_hash,
    token_count,
    vector_id,
    page_number,
    section_title,
    paragraph_index,
    location_text,
    metadata_json,
    created_at,
    updated_at
FROM public.document_chunk
ON CONFLICT DO NOTHING;

INSERT INTO document.document_vector (
    vector_id,
    knowledge_base_id,
    document_id,
    chunk_id,
    chunk_index,
    content,
    embedding,
    metadata_json,
    created_at
)
SELECT
    vector_id,
    knowledge_base_id,
    document_id,
    chunk_id,
    chunk_index,
    content,
    embedding,
    metadata_json,
    created_at
FROM public.document_vector
ON CONFLICT DO NOTHING;

INSERT INTO document.index_job (
    id,
    document_id,
    knowledge_base_id,
    job_type,
    status,
    progress,
    error_message,
    started_at,
    finished_at,
    created_at,
    updated_at
)
SELECT
    id,
    document_id,
    knowledge_base_id,
    job_type,
    status,
    progress,
    error_message,
    started_at,
    finished_at,
    created_at,
    updated_at
FROM public.index_job
ON CONFLICT DO NOTHING;

INSERT INTO chat.chat_session (
    id,
    knowledge_base_id,
    title,
    created_at,
    updated_at
)
SELECT
    id,
    knowledge_base_id,
    title,
    created_at,
    updated_at
FROM public.chat_session
ON CONFLICT DO NOTHING;

INSERT INTO chat.chat_message (
    id,
    session_id,
    role,
    content,
    citations_json,
    created_at
)
SELECT
    id,
    session_id,
    role,
    content,
    citations_json,
    created_at
FROM public.chat_message
ON CONFLICT DO NOTHING;

-- 迁移显式 id 后同步各表序列，避免后续新增数据出现主键冲突。
SELECT setval(pg_get_serial_sequence('auth.user_account', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM auth.user_account), 1), 1), true);
SELECT setval(pg_get_serial_sequence('knowledge.knowledge_base', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM knowledge.knowledge_base), 1), 1), true);
SELECT setval(pg_get_serial_sequence('document.document', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM document.document), 1), 1), true);
SELECT setval(pg_get_serial_sequence('document.document_chunk', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM document.document_chunk), 1), 1), true);
SELECT setval(pg_get_serial_sequence('document.index_job', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM document.index_job), 1), 1), true);
SELECT setval(pg_get_serial_sequence('chat.chat_session', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM chat.chat_session), 1), 1), true);
SELECT setval(pg_get_serial_sequence('chat.chat_message', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM chat.chat_message), 1), 1), true);

COMMIT;
