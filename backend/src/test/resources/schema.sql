-- Test schema for H2 in PostgreSQL compatibility mode (ADR-008)

CREATE ALIAS IF NOT EXISTS CURRENT_SETTING FOR "com.resolveiq.backend.security.H2Functions.currentSetting";

CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    plan_tier VARCHAR(50) NOT NULL DEFAULT 'ENTERPRISE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_tenant_email UNIQUE(tenant_id, email)
);

CREATE TABLE IF NOT EXISTS roles (
    name VARCHAR(50) PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);

MERGE INTO roles (name, description) KEY(name) VALUES
    ('OWNER', 'Full control: billing, org settings, all Admin capabilities, can delete org'),
    ('ADMIN', 'Manage users/roles, integrations, collectors, API keys, retention settings'),
    ('INCIDENT_MANAGER', 'Full incident lifecycle control across all projects; read access to evidence/RCA'),
    ('SRE', 'Full incident lifecycle within assigned projects; manage rules, trigger AI investigation'),
    ('DEVELOPER', 'Read/comment on incidents for assigned services; read-only rules'),
    ('VIEWER', 'Read-only access to incidents, dashboards, and RCA');

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_projects_tenant_slug UNIQUE(tenant_id, slug)
);

CREATE TABLE IF NOT EXISTS environments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    is_production BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_environments_tenant_project_name UNIQUE(tenant_id, project_id, name)
);

CREATE TABLE IF NOT EXISTS services (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    tier VARCHAR(50) NOT NULL DEFAULT 'TIER_1',
    owner_team VARCHAR(255) NOT NULL,
    repo_url VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_services_tenant_project_name UNIQUE(tenant_id, project_id, name)
);

CREATE TABLE IF NOT EXISTS dependencies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    upstream_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    downstream_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    call_type VARCHAR(50) NOT NULL DEFAULT 'HTTP',
    health_status VARCHAR(50) NOT NULL DEFAULT 'HEALTHY',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_dependencies_tenant_services UNIQUE(tenant_id, upstream_service_id, downstream_service_id)
);

CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(50) NOT NULL,
    hashed_secret VARCHAR(255) NOT NULL,
    scopes VARCHAR(255) NOT NULL DEFAULT 'ALL',
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_id UUID,
    actor_type VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_resource VARCHAR(255) NOT NULL,
    before_state TEXT,
    after_state TEXT,
    ip_address VARCHAR(100),
    trace_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Register DB-level immutability trigger on audit_logs
CREATE TRIGGER IF NOT EXISTS trg_audit_logs_no_update_or_delete
BEFORE UPDATE, DELETE ON audit_logs
FOR EACH ROW CALL "com.resolveiq.backend.security.H2AuditLogTrigger";

CREATE TABLE IF NOT EXISTS incidents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    fingerprint VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL DEFAULT 'P2',
    title VARCHAR(500) NOT NULL,
    root_service VARCHAR(255) NOT NULL,
    affected_services TEXT NOT NULL DEFAULT '[]',
    owning_team VARCHAR(255),
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS incident_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    actor_id UUID,
    summary TEXT NOT NULL,
    payload TEXT,
    reference_event_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TRIGGER IF NOT EXISTS trg_incident_events_no_update_or_delete
BEFORE UPDATE, DELETE ON incident_events
FOR EACH ROW CALL "com.resolveiq.backend.security.H2IncidentEventTrigger";

CREATE TABLE IF NOT EXISTS evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,
    service VARCHAR(255) NOT NULL,
    query_used TEXT NOT NULL,
    result_reference VARCHAR(500) NOT NULL,
    payload_summary TEXT NOT NULL,
    relevance_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    relationship_to_hypothesis VARCHAR(100),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS investigations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    model_identifier VARCHAR(100),
    prompt_version VARCHAR(50),
    tool_call_count INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    summary TEXT,
    uncertainty_statement TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS root_cause_candidates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    investigation_id UUID NOT NULL REFERENCES investigations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    rank INT NOT NULL,
    hypothesis TEXT NOT NULL,
    root_service VARCHAR(255) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    reasoning TEXT NOT NULL,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'UNVERIFIED',
    verified_by UUID REFERENCES users(id) ON DELETE SET NULL,
    verification_notes TEXT,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS candidate_evidence (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES root_cause_candidates(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS feedback (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    candidate_id UUID REFERENCES root_cause_candidates(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    verification_status VARCHAR(50) NOT NULL,
    comment TEXT,
    rating INT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_docs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    doc_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_uri VARCHAR(1000),
    service VARCHAR(255),
    environment VARCHAR(100),
    raw_content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    doc_id UUID NOT NULL REFERENCES knowledge_docs(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    chunk_text TEXT NOT NULL,
    token_count INT NOT NULL,
    embedding TEXT,
    header_path VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS deployments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    service_id UUID REFERENCES services(id) ON DELETE CASCADE,
    service_name VARCHAR(255) NOT NULL,
    environment VARCHAR(100) NOT NULL DEFAULT 'production',
    version VARCHAR(100) NOT NULL,
    commit_sha VARCHAR(100) NOT NULL,
    commit_message TEXT,
    deployed_by VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    deployed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata TEXT
);

CREATE TABLE IF NOT EXISTS notification_channels (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    destination VARCHAR(1024) NOT NULL,
    secret_token VARCHAR(255),
    min_severity VARCHAR(32) NOT NULL DEFAULT 'SEV3',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID REFERENCES incidents(id) ON DELETE CASCADE,
    channel_id UUID REFERENCES notification_channels(id) ON DELETE SET NULL,
    channel_type VARCHAR(32) NOT NULL,
    destination VARCHAR(1024) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS code_repositories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    access_token TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_code_repos_tenant_repo UNIQUE (tenant_id, provider, repo_name)
);


