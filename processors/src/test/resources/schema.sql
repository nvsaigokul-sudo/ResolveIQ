-- H2 test schema for metrics_raw table (ADR-008)

CREATE TABLE IF NOT EXISTS metrics_raw (
    time TIMESTAMP WITH TIME ZONE NOT NULL,
    tenant_id UUID NOT NULL,
    project_id UUID,
    service_id VARCHAR(128) NOT NULL,
    environment VARCHAR(64) NOT NULL DEFAULT 'production',
    metric_name VARCHAR(256) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(32),
    dimensions VARCHAR(4000),
    dimensions_hash VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_metrics_raw PRIMARY KEY (time, tenant_id, service_id, metric_name, dimensions_hash, event_id)
);

CREATE INDEX IF NOT EXISTS idx_metrics_tenant_service_name_time 
    ON metrics_raw (tenant_id, service_id, metric_name, time DESC);

CREATE INDEX IF NOT EXISTS idx_metrics_tenant_time 
    ON metrics_raw (tenant_id, time DESC);

CREATE INDEX IF NOT EXISTS idx_metrics_event_id 
    ON metrics_raw (event_id);
