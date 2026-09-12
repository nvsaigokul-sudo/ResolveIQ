package com.resolveiq.backend.exception;

import com.resolveiq.common.incident.IncidentStatus;

public class InvalidLifecycleTransitionException extends RuntimeException {

    private final IncidentStatus fromStatus;
    private final IncidentStatus toStatus;

    public InvalidLifecycleTransitionException(IncidentStatus fromStatus, IncidentStatus toStatus, String message) {
        super(message);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public IncidentStatus getFromStatus() {
        return fromStatus;
    }

    public IncidentStatus getToStatus() {
        return toStatus;
    }
}
