package com.resolveiq.common.telemetry;

/**
 * Anomaly Lifecycle State Machine conforming strictly to PRD Section 16, 17, 51.
 * States: OBSERVED -> EVALUATED -> RAISED -> DEDUPLICATED -> SUPPRESSED -> RESOLVED.
 */
public enum AnomalyLifecycleState {
    OBSERVED,
    EVALUATED,
    RAISED,
    DEDUPLICATED,
    SUPPRESSED,
    RESOLVED
}
