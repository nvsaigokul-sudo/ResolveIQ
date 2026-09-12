package com.resolveiq.common.exception;

import java.util.Map;

public class UnauthorizedException extends ResolveIQException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public UnauthorizedException(String message, Map<String, Object> details) {
        super("UNAUTHORIZED", message, details);
    }
}
