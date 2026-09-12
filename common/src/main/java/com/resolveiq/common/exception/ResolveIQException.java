package com.resolveiq.common.exception;

import java.util.Collections;
import java.util.Map;

public class ResolveIQException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> details;

    public ResolveIQException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public ResolveIQException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details != null ? details : Collections.emptyMap();
    }

    public ResolveIQException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
