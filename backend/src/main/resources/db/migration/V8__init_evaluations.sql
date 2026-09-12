-- ============================================================================
-- V8: AI Evaluation Framework & Benchmarking Schema (PRD §§30, 56, 57, 58, 60, 68.6)
-- ============================================================================

-- 1. Evaluation Runs Table
CREATE TABLE IF NOT EXISTS evaluation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    model_identifier VARCHAR(128) NOT NULL,
    benchmark_suite VARCHAR(128) NOT NULL,
    total_cases INT NOT NULL DEFAULT 0,
    passed_cases INT NOT NULL DEFAULT 0,
    top1_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    top3_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    evidence_precision DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    evidence_grounding DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    hallucination_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    insufficient_evidence_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    completion_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    avg_tool_calls DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    p50_duration_ms BIGINT NOT NULL DEFAULT 0,
    p95_duration_ms BIGINT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    estimated_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    regression_detected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_eval_runs_tenant_created ON evaluation_runs(tenant_id, created_at DESC);

ALTER TABLE evaluation_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE evaluation_runs FORCE ROW LEVEL SECURITY;

CREATE POLICY evaluation_runs_tenant_isolation_policy ON evaluation_runs
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 2. Evaluation Case Results Table
CREATE TABLE IF NOT EXISTS evaluation_case_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    scenario_id VARCHAR(128) NOT NULL,
    scenario_category VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PASSED',
    expected_service VARCHAR(128),
    predicted_service VARCHAR(128),
    top1_match BOOLEAN NOT NULL DEFAULT FALSE,
    top3_match BOOLEAN NOT NULL DEFAULT FALSE,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    evidence_grounded BOOLEAN NOT NULL DEFAULT FALSE,
    hallucination_detected BOOLEAN NOT NULL DEFAULT FALSE,
    insufficient_evidence_correct BOOLEAN NOT NULL DEFAULT FALSE,
    tool_calls INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    tokens_used INT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    metrics_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_eval_cases_tenant_run ON evaluation_case_results(tenant_id, run_id);
CREATE INDEX IF NOT EXISTS idx_eval_cases_scenario ON evaluation_case_results(tenant_id, scenario_id);

ALTER TABLE evaluation_case_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE evaluation_case_results FORCE ROW LEVEL SECURITY;

CREATE POLICY evaluation_case_results_tenant_isolation_policy ON evaluation_case_results
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
