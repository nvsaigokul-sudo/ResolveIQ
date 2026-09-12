-- ResolveIQ Deployments Schema Migration (V6)
-- Implements PRD Section 25.5 (Deployments Tool), Section 36.1 (Deployments Table)

CREATE TABLE IF NOT EXISTS deployments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    deployed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_deployments_tenant_service ON deployments(tenant_id, service_name, deployed_at DESC);
CREATE INDEX IF NOT EXISTS idx_deployments_tenant_created ON deployments(tenant_id, deployed_at DESC);

-- Enable and force PostgreSQL Row Level Security (RLS)
ALTER TABLE deployments ENABLE ROW LEVEL SECURITY;
ALTER TABLE deployments FORCE ROW LEVEL SECURITY;

CREATE POLICY deployments_tenant_isolation_policy ON deployments
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
