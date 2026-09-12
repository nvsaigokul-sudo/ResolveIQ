package com.resolveiq.backend.service;

import com.resolveiq.backend.exception.InvalidLifecycleTransitionException;
import com.resolveiq.common.incident.IncidentEventType;
import com.resolveiq.common.incident.IncidentStatus;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Guarded incident lifecycle state machine enforcing PRD §19.1:
 * DETECTED -> INVESTIGATING -> IDENTIFIED -> MITIGATING -> MONITORING -> RESOLVED -> CLOSED
 *
 * "Transitions are guarded (e.g., cannot go directly from DETECTED to RESOLVED without passing
 * through INVESTIGATING, though IDENTIFIED can be skipped if manually resolved) and every
 * transition is an immutable, auditable timeline event."
 */
@Component
public class IncidentLifecycleStateMachine {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        // From DETECTED: must enter INVESTIGATING (or IDENTIFIED if instantly recognized)
        ALLOWED_TRANSITIONS.put(IncidentStatus.DETECTED, EnumSet.of(
                IncidentStatus.INVESTIGATING,
                IncidentStatus.IDENTIFIED
        ));

        // From INVESTIGATING: can advance to IDENTIFIED, MITIGATING, MONITORING, or direct RESOLVED
        ALLOWED_TRANSITIONS.put(IncidentStatus.INVESTIGATING, EnumSet.of(
                IncidentStatus.IDENTIFIED,
                IncidentStatus.MITIGATING,
                IncidentStatus.MONITORING,
                IncidentStatus.RESOLVED
        ));

        // From IDENTIFIED: can advance to MITIGATING, MONITORING, RESOLVED, or fall back to INVESTIGATING
        ALLOWED_TRANSITIONS.put(IncidentStatus.IDENTIFIED, EnumSet.of(
                IncidentStatus.MITIGATING,
                IncidentStatus.MONITORING,
                IncidentStatus.RESOLVED,
                IncidentStatus.INVESTIGATING
        ));

        // From MITIGATING: can advance to MONITORING, RESOLVED, or back to INVESTIGATING (if mitigation failed)
        ALLOWED_TRANSITIONS.put(IncidentStatus.MITIGATING, EnumSet.of(
                IncidentStatus.MONITORING,
                IncidentStatus.RESOLVED,
                IncidentStatus.INVESTIGATING
        ));

        // From MONITORING: can advance to RESOLVED, or fall back to INVESTIGATING (if regression re-emerges)
        ALLOWED_TRANSITIONS.put(IncidentStatus.MONITORING, EnumSet.of(
                IncidentStatus.RESOLVED,
                IncidentStatus.INVESTIGATING
        ));

        // From RESOLVED: can advance to CLOSED, or reopen to INVESTIGATING (if recurrence within reopen window)
        ALLOWED_TRANSITIONS.put(IncidentStatus.RESOLVED, EnumSet.of(
                IncidentStatus.CLOSED,
                IncidentStatus.INVESTIGATING
        ));

        // From CLOSED: terminal state, can only be explicitly reopened to INVESTIGATING
        ALLOWED_TRANSITIONS.put(IncidentStatus.CLOSED, EnumSet.of(
                IncidentStatus.INVESTIGATING
        ));
    }

    public void validateTransition(IncidentStatus current, IncidentStatus target) {
        if (current == null || target == null) {
            throw new IllegalArgumentException("Current status and target status must not be null");
        }
        if (current == target) {
            return; // No-op transition
        }

        Set<IncidentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Collections.emptySet());
        if (!allowed.contains(target)) {
            throw new InvalidLifecycleTransitionException(
                    current,
                    target,
                    String.format("Invalid lifecycle transition from %s to %s. PRD §19.1 prohibits skipping mandatory lifecycle phases.", current, target)
            );
        }
    }

    public IncidentEventType getTimelineEventTypeForTransition(IncidentStatus target) {
        return switch (target) {
            case DETECTED -> IncidentEventType.ANOMALY_DETECTED;
            case INVESTIGATING -> IncidentEventType.INVESTIGATION_STARTED;
            case IDENTIFIED -> IncidentEventType.HYPOTHESIS_GENERATED;
            case MITIGATING -> IncidentEventType.MITIGATION_APPLIED;
            case MONITORING -> IncidentEventType.MONITORING_STARTED;
            case RESOLVED -> IncidentEventType.RESOLVED;
            case CLOSED -> IncidentEventType.CLOSED;
        };
    }
}
