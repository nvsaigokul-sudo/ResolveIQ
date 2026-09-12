package com.resolveiq.common.exception;

import java.util.Map;

public class ValidationException extends ResolveIQException {
    public ValidationException(String message) {
        super("VALIDATION_FAILED", message);
    }

    public ValidationException(String message, Map<String, Object> details) {
        super("VALIDATION_FAILED", message, details);
    }
}
