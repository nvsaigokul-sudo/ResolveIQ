package com.resolveiq.common.telemetry;

/**
 * Standard severity tiers for anomalies, alerts, and incidents (PRD §12, §16, §20).
 */
public enum Severity {
    CRITICAL, // P1: Full outage, data corruption, critical financial impact
    HIGH,     // P2: Core service degradation, significant customer impact
    MEDIUM,   // P3: Partial degradation, non-blocking customer issue
    LOW,      // P4: Minor anomaly, latency blip without failure
    INFO      // P5: Informational observation or telemetry shift
}
