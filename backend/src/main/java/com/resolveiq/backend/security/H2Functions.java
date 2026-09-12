package com.resolveiq.backend.security;

import com.resolveiq.common.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * H2 user-defined function emulating PostgreSQL's current_setting('app.tenant_id', true)
 * for dual-mode testing without requiring external container dependencies (ADR-008).
 */
public final class H2Functions {

    private H2Functions() {
    }

    public static String currentSetting(String settingName, Boolean missingOk) {
        if ("app.tenant_id".equals(settingName)) {
            return TenantContextHolder.getContext()
                    .map(ctx -> ctx.tenantId().toString())
                    .orElse(null);
        }
        return null;
    }

    public static String currentSetting(String settingName) {
        return currentSetting(settingName, true);
    }
}
