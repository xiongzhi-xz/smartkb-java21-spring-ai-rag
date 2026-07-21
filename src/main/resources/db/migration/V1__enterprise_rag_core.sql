CREATE TABLE knowledge_base (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE kb_document (
    id UUID PRIMARY KEY,
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_base(id),
    file_name VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_checksum VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (knowledge_base_id, content_checksum, version_no)
);

CREATE TABLE document_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, ordinal)
);

CREATE TABLE ingestion_job (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversation (
    id VARCHAR(128) PRIMARY KEY,
    title VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    next_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMPTZ
);

CREATE TABLE conversation_message (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    sequence_no BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    citations JSONB,
    trace_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (conversation_id, sequence_no)
);

CREATE TABLE retrieval_trace (
    id UUID PRIMARY KEY,
    conversation_message_id UUID REFERENCES conversation_message(id) ON DELETE SET NULL,
    query_text TEXT NOT NULL,
    candidates JSONB,
    rerank_mode VARCHAR(64),
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_document_status ON kb_document (knowledge_base_id, status, updated_at DESC);
CREATE INDEX idx_document_chunk_status ON document_chunk (document_id, index_status, ordinal);
CREATE INDEX idx_ingestion_job_status ON ingestion_job (status, created_at);
CREATE INDEX idx_conversation_message_recent ON conversation_message (conversation_id, sequence_no DESC);
CREATE INDEX idx_retrieval_trace_message ON retrieval_trace (conversation_message_id, created_at DESC);
