CREATE TABLE IF NOT EXISTS chat.chat_session (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_schema_session_kb ON chat.chat_session(knowledge_base_id);

CREATE TABLE IF NOT EXISTS chat.chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    citations_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_schema_message_session ON chat.chat_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS chat.undo_log (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info BYTEA NOT NULL,
    log_status INTEGER NOT NULL,
    log_created TIMESTAMP NOT NULL,
    log_modified TIMESTAMP NOT NULL,
    CONSTRAINT ux_chat_undo_log UNIQUE (xid, branch_id)
);
