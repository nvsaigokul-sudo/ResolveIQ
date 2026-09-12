package com.resolveiq.common.exception;

import java.util.Map;

public class ResourceNotFoundException extends ResolveIQException {
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }

    public ResourceNotFoundException(String message, Map<String, Object> details) {
        super("RESOURCE_NOT_FOUND", message, details);
    }
}
