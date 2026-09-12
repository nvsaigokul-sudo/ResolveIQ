-- ==============================================================================
-- ResolveIQ Schema Migration V4: Incidents & Evidence Engine
-- Implements PRD §§19, 20, 21, 36.1, 36.2, 57 (Incident Lifecycle, Timeline, Evidence, RCA Candidates, RLS)
-- ==============================================================================

-- 1. Incidents Table (PRD §19, §36.2)
CREATE TABLE IF NOT EXISTS incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    fingerprint TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('DETECTED','INVESTIGATING','IDENTIFIED','MITIGATING','MONITORING','RESOLVED','CLOSED')),
    severity TEXT NOT NULL CHECK (severity IN ('SEV1','SEV2','SEV3','SEV4')),
    priority TEXT NOT NULL DEFAULT 'P2',
    title TEXT NOT NULL,
    root_service TEXT NOT NULL,
    affected_services TEXT NOT NULL DEFAULT '[]',
    owning_team TEXT,
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ
);

CREATE INDEX idx_incidents_tenant_status ON incidents(tenant_id, status);
CREATE INDEX idx_incidents_tenant_fingerprint ON incidents(tenant_id, fingerprint);
CREATE INDEX idx_incidents_tenant_created ON incidents(tenant_id, created_at DESC);

ALTER TABLE incidents ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_incidents ON incidents
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 2. Incident Events (Incident Timeline - PRD §20, Append-only)
CREATE TABLE IF NOT EXISTS incident_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (event_type IN (
        'ANOMALY_DETECTED', 'ALERT_GENERATED', 'CORRELATION_DISCOVERED',
        'DEPLOYMENT_OBSERVED', 'CONFIG_CHANGE_OBSERVED', 'INVESTIGATION_STARTED',
        'INVESTIGATION_COMPLETED', 'HYPOTHESIS_GENERATED', 'EVIDENCE_DISCOVERED',
        'HUMAN_COMMENT', 'SEVERITY_CHANGED', 'ASSIGNMENT_CHANGED',
        'MITIGATION_APPLIED', 'MONITORING_STARTED', 'RESOLVED', 'CLOSED'
    )),
    actor_type TEXT NOT NULL DEFAULT 'SYSTEM',
    actor_id UUID,
    summary TEXT NOT NULL,
    payload TEXT,
    reference_event_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_incident_events_tenant_incident ON incident_events(tenant_id, incident_id, created_at ASC);

ALTER TABLE incident_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_incident_events ON incident_events
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- DB Immutability trigger for incident_events (PRD §20: "No timeline event is ever updated or deleted")
CREATE TRIGGER trg_incident_events_no_update_or_delete
BEFORE UPDATE OR DELETE ON incident_events
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();

-- 3. Evidence Table (PRD §21, §36.1, §36.2)
CREATE TABLE IF NOT EXISTS evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    source TEXT NOT NULL CHECK (source IN (
        'METRICS', 'LOGS', 'TRACES', 'DEPLOYMENT', 'CONFIG',
        'HISTORY', 'RUNBOOK', 'CODE', 'SERVICE_HEALTH', 'ANOMALY'
    )),
    service TEXT NOT NULL,
    query_used TEXT NOT NULL,
    result_reference TEXT NOT NULL,
    payload_summary TEXT NOT NULL,
    relevance_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    relationship_to_hypothesis TEXT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evidence_tenant_incident ON evidence(tenant_id, incident_id, created_at DESC);
CREATE INDEX idx_evidence_tenant_source ON evidence(tenant_id, source);

ALTER TABLE evidence ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_evidence ON evidence
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 4. Investigations Table (PRD §36.1, §36.2)
CREATE TABLE IF NOT EXISTS investigations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','TIMED_OUT','INSUFFICIENT_EVIDENCE','AI_UNAVAILABLE')),
    model_identifier TEXT,
    prompt_version TEXT,
    tool_call_count INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    summary TEXT,
    uncertainty_statement TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_investigations_tenant_incident ON investigations(tenant_id, incident_id);

ALTER TABLE investigations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_investigations ON investigations
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 5. Root Cause Candidates (PRD §19.3, §24, §36.1, §57)
CREATE TABLE IF NOT EXISTS root_cause_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    investigation_id UUID NOT NULL REFERENCES investigations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    rank INT NOT NULL,
    hypothesis TEXT NOT NULL,
    root_service TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    reasoning TEXT NOT NULL,
    verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED','NEEDS_MORE_EVIDENCE')),
    verified_by UUID REFERENCES users(id) ON DELETE SET NULL,
    verification_notes TEXT,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_candidates_tenant_incident ON root_cause_candidates(tenant_id, incident_id);
CREATE INDEX idx_candidates_tenant_investigation ON root_cause_candidates(tenant_id, investigation_id);

ALTER TABLE root_cause_candidates ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_root_cause_candidates ON root_cause_candidates
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 6. Candidate Evidence Join Table (PRD §30, §36.2)
CREATE TABLE IF NOT EXISTS candidate_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES root_cause_candidates(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('SUPPORTING','COUNTER','NEUTRAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cand_evidence_tenant_cand ON candidate_evidence(tenant_id, candidate_id);
CREATE INDEX idx_cand_evidence_tenant_evid ON candidate_evidence(tenant_id, evidence_id);

ALTER TABLE candidate_evidence ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_candidate_evidence ON candidate_evidence
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 7. Feedback Table (PRD §36.1, §57)
CREATE TABLE IF NOT EXISTS feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    candidate_id UUID REFERENCES root_cause_candidates(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    verification_status TEXT NOT NULL,
    comment TEXT,
    rating INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_tenant_incident ON feedback(tenant_id, incident_id);

ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_feedback ON feedback
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
