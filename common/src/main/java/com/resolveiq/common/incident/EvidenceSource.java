package com.resolveiq.common.incident;

/**
 * Sources of structured evidence according to PRD §21.1.
 */
public enum EvidenceSource {
    METRICS,
    LOGS,
    TRACES,
    DEPLOYMENT,
    CONFIG,
    TOPOLOGY,
    HISTORY,
    RUNBOOK,
    CODE,
    SERVICE_HEALTH,
    ANOMALY
}
