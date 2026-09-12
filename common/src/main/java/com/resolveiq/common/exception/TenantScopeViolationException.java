package com.resolveiq.common.exception;

import java.util.Map;

public class TenantScopeViolationException extends ResolveIQException {
    public TenantScopeViolationException(String message) {
        super("TENANT_SCOPE_VIOLATION", message);
    }

    public TenantScopeViolationException(String message, Map<String, Object> details) {
        super("TENANT_SCOPE_VIOLATION", message, details);
    }
}
