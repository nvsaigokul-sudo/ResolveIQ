package com.resolveiq.common.incident;

/**
 * Canonical incident lifecycle states according to PRD §19.1.
 * Flow: DETECTED -> INVESTIGATING -> IDENTIFIED -> MITIGATING -> MONITORING -> RESOLVED -> CLOSED
 */
public enum IncidentStatus {
    DETECTED,
    INVESTIGATING,
    IDENTIFIED,
    MITIGATING,
    MONITORING,
    RESOLVED,
    CLOSED;

    public boolean isTerminal() {
        return this == CLOSED;
    }

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED;
    }
}
