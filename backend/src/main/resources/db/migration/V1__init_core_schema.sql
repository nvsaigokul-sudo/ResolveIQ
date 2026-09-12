-- ResolveIQ Core Schema Migration (V1)
-- Implements PRD Section 11 (Multi-Tenancy), Section 12 (Auth/RBAC), Section 36 (Database Design), Section 39 (Audit)

-- Enable UUID extension if available
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Organizations (Tenants)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    plan_tier TEXT NOT NULL DEFAULT 'ENTERPRISE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT NOT NULL,
    password_hash TEXT,
    role TEXT NOT NULL CHECK (role IN ('OWNER','ADMIN','INCIDENT_MANAGER','SRE','DEVELOPER','VIEWER')),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE(tenant_id, email)
);
CREATE INDEX idx_users_tenant ON users(tenant_id);

-- 3. Roles reference table
CREATE TABLE roles (
    name TEXT PRIMARY KEY,
    description TEXT NOT NULL
);

INSERT INTO roles (name, description) VALUES
    ('OWNER', 'Full control: billing, org settings, all Admin capabilities, can delete org'),
    ('ADMIN', 'Manage users/roles, integrations, collectors, API keys, retention settings'),
    ('INCIDENT_MANAGER', 'Full incident lifecycle control across all projects; read access to evidence/RCA'),
    ('SRE', 'Full incident lifecycle within assigned projects; manage rules, trigger AI investigation'),
    ('DEVELOPER', 'Read/comment on incidents for assigned services; read-only rules'),
    ('VIEWER', 'Read-only access to incidents, dashboards, and RCA');

-- 4. Projects
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    slug TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_tenant_slug UNIQUE(tenant_id, slug)
);
CREATE INDEX idx_projects_tenant ON projects(tenant_id);

-- 5. Environments
CREATE TABLE environments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    is_production BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_environments_tenant_project_name UNIQUE(tenant_id, project_id, name)
);
CREATE INDEX idx_environments_tenant ON environments(tenant_id);

-- 6. Services
CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    tier TEXT NOT NULL DEFAULT 'TIER_1',
    owner_team TEXT NOT NULL,
    repo_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_services_tenant_project_name UNIQUE(tenant_id, project_id, name)
);
CREATE INDEX idx_services_tenant ON services(tenant_id);

-- 7. Dependencies (Service-to-Service call edges)
CREATE TABLE dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    upstream_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    downstream_service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    call_type TEXT NOT NULL DEFAULT 'HTTP',
    health_status TEXT NOT NULL DEFAULT 'HEALTHY',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dependencies_tenant_services UNIQUE(tenant_id, upstream_service_id, downstream_service_id)
);
CREATE INDEX idx_dependencies_tenant ON dependencies(tenant_id);

-- 8. API Keys (PRD Section 12.1)
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    key_prefix TEXT NOT NULL,
    hashed_secret TEXT NOT NULL,
    scopes TEXT NOT NULL DEFAULT 'ALL',
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix);

-- 9. Audit Logs (PRD Section 39 - Append-only)
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_id UUID,
    actor_type TEXT NOT NULL,
    action TEXT NOT NULL,
    target_resource TEXT NOT NULL,
    before_state TEXT,
    after_state TEXT,
    ip_address TEXT,
    trace_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at DESC);
