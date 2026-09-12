-- ============================================================================
-- V7: Notification Service & Code-Aware Integrations Schema (PRD §§26, 27, 35, 36.1, 51, 52)
-- ============================================================================

-- 1. Notification Channels
CREATE TABLE IF NOT EXISTS notification_channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    destination VARCHAR(1024) NOT NULL,
    secret_token VARCHAR(255),
    min_severity VARCHAR(32) NOT NULL DEFAULT 'SEV3',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_channels_tenant ON notification_channels(tenant_id, enabled);

ALTER TABLE notification_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_channels FORCE ROW LEVEL SECURITY;

CREATE POLICY notification_channels_tenant_isolation_policy ON notification_channels
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 2. Notification Delivery Tracking & Dead-Letter Queue (PRD §27)
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    next_retry_at TIMESTAMPTZ,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_status ON notifications(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_incident ON notifications(tenant_id, incident_id);
CREATE INDEX IF NOT EXISTS idx_notifications_idempotency ON notifications(tenant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_notifications_next_retry ON notifications(status, next_retry_at) WHERE status = 'RETRYING';

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications FORCE ROW LEVEL SECURITY;

CREATE POLICY notifications_tenant_isolation_policy ON notifications
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 3. Code-Aware Integrations: Authorized Repositories (PRD §26)
CREATE TABLE IF NOT EXISTS code_repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    access_token TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_code_repos_tenant_repo UNIQUE (tenant_id, provider, repo_name)
);

CREATE INDEX IF NOT EXISTS idx_code_repositories_tenant_repo ON code_repositories(tenant_id, repo_name);

ALTER TABLE code_repositories ENABLE ROW LEVEL SECURITY;
ALTER TABLE code_repositories FORCE ROW LEVEL SECURITY;

CREATE POLICY code_repositories_tenant_isolation_policy ON code_repositories
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
