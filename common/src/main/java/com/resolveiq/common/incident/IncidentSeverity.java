package com.resolveiq.common.incident;

/**
 * Incident severity levels according to PRD §19.2 (Sev1 - Sev4).
 */
public enum IncidentSeverity {
    SEV1(1),
    SEV2(2),
    SEV3(3),
    SEV4(4);

    private final int level;

    IncidentSeverity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Checks if this severity is higher (more severe) than another.
     * Lower numeric level indicates higher severity (SEV1 > SEV2).
     */
    public boolean isHigherThan(IncidentSeverity other) {
        if (other == null) {
            return true;
        }
        return this.level < other.level;
    }

    public static IncidentSeverity fromLevel(int level) {
        for (IncidentSeverity s : values()) {
            if (s.level == level) {
                return s;
            }
        }
        return SEV3;
    }
}
