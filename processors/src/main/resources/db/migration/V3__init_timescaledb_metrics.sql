-- ============================================================================
-- ResolveIQ TimescaleDB Metrics Schema & Continuous Rollups (PRD §10.3, §13, §36, §37)
-- ============================================================================

-- Attempt to create TimescaleDB extension if available
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- 1. Raw Metrics Table
CREATE TABLE IF NOT EXISTS metrics_raw (
    time TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL,
    project_id UUID,
    service_id VARCHAR(128) NOT NULL,
    environment VARCHAR(64) NOT NULL DEFAULT 'production',
    metric_name VARCHAR(256) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(32),
    dimensions JSONB DEFAULT '{}'::jsonb,
    dimensions_hash VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_metrics_raw PRIMARY KEY (time, tenant_id, service_id, metric_name, dimensions_hash, event_id)
);

-- Indices for high-throughput tenant querying and anomaly detection
CREATE INDEX IF NOT EXISTS idx_metrics_tenant_service_name_time 
    ON metrics_raw (tenant_id, service_id, metric_name, time DESC);

CREATE INDEX IF NOT EXISTS idx_metrics_tenant_time 
    ON metrics_raw (tenant_id, time DESC);

CREATE INDEX IF NOT EXISTS idx_metrics_event_id 
    ON metrics_raw (event_id);

-- 2. PostgreSQL Row-Level Security (RLS) Policy (Layer 3 Defense-in-Depth)
ALTER TABLE metrics_raw ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'metrics_raw' AND policyname = 'metrics_tenant_isolation'
    ) THEN
        CREATE POLICY metrics_tenant_isolation ON metrics_raw
            FOR ALL
            USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
    END IF;
END $$;

-- 3. TimescaleDB Hypertable & Continuous Aggregate Views
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        -- Convert table to hypertable partitioned by time with 1-day chunks
        PERFORM create_hypertable('metrics_raw', 'time', if_not_exists => TRUE, chunk_time_interval => INTERVAL '1 day');

        -- Continuous aggregate: 1-minute rollup
        IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'metrics_1m') THEN
            CREATE MATERIALIZED VIEW metrics_1m
            WITH (timescaledb.continuous) AS
            SELECT time_bucket('1 minute', time) AS bucket,
                   tenant_id,
                   service_id,
                   metric_name,
                   count(*) AS sample_count,
                   min(metric_value) AS val_min,
                   max(metric_value) AS val_max,
                   avg(metric_value) AS val_avg,
                   sum(metric_value) AS val_sum
            FROM metrics_raw
            GROUP BY bucket, tenant_id, service_id, metric_name;

            PERFORM add_continuous_aggregate_policy('metrics_1m', 
                start_offset => INTERVAL '2 hours', 
                end_offset => INTERVAL '1 minute', 
                schedule_interval => INTERVAL '1 minute');
        END IF;

        -- Continuous aggregate: 5-minute rollup
        IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'metrics_5m') THEN
            CREATE MATERIALIZED VIEW metrics_5m
            WITH (timescaledb.continuous) AS
            SELECT time_bucket('5 minutes', time) AS bucket,
                   tenant_id,
                   service_id,
                   metric_name,
                   count(*) AS sample_count,
                   min(metric_value) AS val_min,
                   max(metric_value) AS val_max,
                   avg(metric_value) AS val_avg,
                   sum(metric_value) AS val_sum
            FROM metrics_raw
            GROUP BY bucket, tenant_id, service_id, metric_name;

            PERFORM add_continuous_aggregate_policy('metrics_5m', 
                start_offset => INTERVAL '12 hours', 
                end_offset => INTERVAL '5 minutes', 
                schedule_interval => INTERVAL '5 minutes');
        END IF;

        -- Continuous aggregate: 1-hour rollup
        IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'metrics_1h') THEN
            CREATE MATERIALIZED VIEW metrics_1h
            WITH (timescaledb.continuous) AS
            SELECT time_bucket('1 hour', time) AS bucket,
                   tenant_id,
                   service_id,
                   metric_name,
                   count(*) AS sample_count,
                   min(metric_value) AS val_min,
                   max(metric_value) AS val_max,
                   avg(metric_value) AS val_avg,
                   sum(metric_value) AS val_sum
            FROM metrics_raw
            GROUP BY bucket, tenant_id, service_id, metric_name;

            PERFORM add_continuous_aggregate_policy('metrics_1h', 
                start_offset => INTERVAL '3 days', 
                end_offset => INTERVAL '1 hour', 
                schedule_interval => INTERVAL '1 hour');
        END IF;

        -- Continuous aggregate: 1-day rollup
        IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'metrics_1d') THEN
            CREATE MATERIALIZED VIEW metrics_1d
            WITH (timescaledb.continuous) AS
            SELECT time_bucket('1 day', time) AS bucket,
                   tenant_id,
                   service_id,
                   metric_name,
                   count(*) AS sample_count,
                   min(metric_value) AS val_min,
                   max(metric_value) AS val_max,
                   avg(metric_value) AS val_avg,
                   sum(metric_value) AS val_sum
            FROM metrics_raw
            GROUP BY bucket, tenant_id, service_id, metric_name;

            PERFORM add_continuous_aggregate_policy('metrics_1d', 
                start_offset => INTERVAL '1 month', 
                end_offset => INTERVAL '1 day', 
                schedule_interval => INTERVAL '1 day');
        END IF;

        -- Retention Policies (PRD §13, §37): Raw metrics 30 days, rollups 13 months
        PERFORM add_retention_policy('metrics_raw', INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metrics_1h', INTERVAL '13 months', if_not_exists => TRUE);
        PERFORM add_retention_policy('metrics_1d', INTERVAL '13 months', if_not_exists => TRUE);
    END IF;
END $$;
