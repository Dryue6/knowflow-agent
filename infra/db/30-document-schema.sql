CREATE TABLE IF NOT EXISTS document.document (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_path VARCHAR(1024) NOT NULL,
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    constraint_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    constraint_priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_schema_document_kb ON document.document(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_document_schema_document_status ON document.document(status);

CREATE TABLE IF NOT EXISTS document.document_chunk (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    vector_id VARCHAR(64),
    page_number INTEGER,
    section_title VARCHAR(255),
    paragraph_index INTEGER,
    location_text VARCHAR(255),
    metadata_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_schema_chunk_document ON document.document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_document_schema_chunk_kb ON document.document_chunk(knowledge_base_id);

CREATE TABLE IF NOT EXISTS document.document_vector (
    vector_id VARCHAR(64) PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    metadata_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_schema_vector_kb ON document.document_vector(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_document_schema_vector_document ON document.document_vector(document_id);
CREATE INDEX IF NOT EXISTS idx_document_schema_vector_embedding ON document.document_vector USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE IF NOT EXISTS document.index_job (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_schema_index_job_document ON document.index_job(document_id);
CREATE INDEX IF NOT EXISTS idx_document_schema_index_job_status ON document.index_job(status);

CREATE TABLE IF NOT EXISTS document.undo_log (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info BYTEA NOT NULL,
    log_status INTEGER NOT NULL,
    log_created TIMESTAMP NOT NULL,
    log_modified TIMESTAMP NOT NULL,
    CONSTRAINT ux_document_undo_log UNIQUE (xid, branch_id)
);
