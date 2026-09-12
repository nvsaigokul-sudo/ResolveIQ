-- ==============================================================================
-- ResolveIQ Phase 7: Historical Intelligence & Tenant-Isolated RAG (PRD §22, §25, §36.1, §36.2)
-- Flyway Migration V5: pgvector extension, knowledge_docs, and knowledge_chunks
-- ==============================================================================

-- 1. Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Knowledge Documents (Postmortems, Runbooks, Architecture Docs, Historical Incidents)
CREATE TABLE IF NOT EXISTS knowledge_docs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    doc_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_uri VARCHAR(1000),
    service VARCHAR(255),
    environment VARCHAR(100),
    raw_content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_knowledge_doc_type CHECK (
        doc_type IN ('POSTMORTEM', 'RUNBOOK', 'HISTORICAL_INCIDENT', 'ARCHITECTURE_DOC', 'TROUBLESHOOTING')
    )
);

-- Indexes for knowledge_docs
CREATE INDEX IF NOT EXISTS idx_kdocs_tenant ON knowledge_docs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kdocs_tenant_type ON knowledge_docs(tenant_id, doc_type);
CREATE INDEX IF NOT EXISTS idx_kdocs_tenant_service ON knowledge_docs(tenant_id, service);
CREATE INDEX IF NOT EXISTS idx_kdocs_tenant_hash ON knowledge_docs(tenant_id, content_hash);

-- 3. Knowledge Chunks (Token-bounded chunks with pgvector embeddings)
CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    doc_id UUID NOT NULL REFERENCES knowledge_docs(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    chunk_text TEXT NOT NULL,
    token_count INT NOT NULL,
    embedding vector(384),
    header_path VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for knowledge_chunks
CREATE INDEX IF NOT EXISTS idx_kchunks_tenant ON knowledge_chunks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kchunks_doc ON knowledge_chunks(doc_id);
CREATE INDEX IF NOT EXISTS idx_kchunks_tenant_doc ON knowledge_chunks(tenant_id, doc_id);

-- HNSW Vector Index for Cosine Distance Similarity
CREATE INDEX IF NOT EXISTS idx_kchunks_embedding_hnsw 
ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. Enable and FORCE PostgreSQL Row-Level Security (RLS)
ALTER TABLE knowledge_docs ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_docs FORCE ROW LEVEL SECURITY;

CREATE POLICY knowledge_docs_tenant_isolation ON knowledge_docs
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

ALTER TABLE knowledge_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_chunks FORCE ROW LEVEL SECURITY;

CREATE POLICY knowledge_chunks_tenant_isolation ON knowledge_chunks
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
