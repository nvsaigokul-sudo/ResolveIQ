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
