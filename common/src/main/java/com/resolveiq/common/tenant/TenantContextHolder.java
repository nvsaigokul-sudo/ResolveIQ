package com.resolveiq.common.tenant;

import com.resolveiq.common.exception.TenantScopeViolationException;

import java.util.Optional;
import java.util.UUID;

/**
 * ThreadLocal container for the active request's TenantContext.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static Optional<TenantContext> getContext() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static TenantContext getRequiredContext() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new TenantScopeViolationException("No authenticated tenant context found on current thread");
        }
        return ctx;
    }

    public static UUID getRequiredTenantId() {
        return getRequiredContext().tenantId();
    }

    public static UUID requireTenantId() {
        return getRequiredTenantId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
