package com.resolveiq.common.incident;

/**
 * Status of an investigation lifecycle according to PRD §36.1 and §61.
 */
public enum InvestigationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    INSUFFICIENT_EVIDENCE,
    AI_UNAVAILABLE
}
