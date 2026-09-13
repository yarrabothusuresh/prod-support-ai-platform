CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    application_name VARCHAR(100) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(500) NOT NULL,
    version VARCHAR(50),
    owner VARCHAR(100),
    content_hash VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_knowledge_doc_app_env_source UNIQUE (application_name, environment, source)
);

CREATE TABLE IF NOT EXISTS vector_store (
    id VARCHAR(36) PRIMARY KEY,
    content CLOB,
    metadata CLOB
);
