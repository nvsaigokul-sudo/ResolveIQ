package com.resolveiq.common.tenant;

import com.resolveiq.common.security.ActorType;
import com.resolveiq.common.security.Role;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable security context holding tenant and principal identity.
 * Strictly derived server-side from JWT claims or hashed API keys (PRD Section 11.2).
 */
public record TenantContext(
        UUID tenantId,
        UUID userId,
        Role role,
        ActorType actorType,
        UUID apiKeyId,
        String traceId
) {
    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null in TenantContext");
        Objects.requireNonNull(role, "role cannot be null in TenantContext");
        Objects.requireNonNull(actorType, "actorType cannot be null in TenantContext");
    }

    public static TenantContext ofUser(UUID tenantId, UUID userId, Role role, String traceId) {
        return new TenantContext(tenantId, userId, role, ActorType.USER, null, traceId);
    }

    public static TenantContext ofApiKey(UUID tenantId, UUID apiKeyId, Role role, String traceId) {
        return new TenantContext(tenantId, null, role, ActorType.API_KEY, apiKeyId, traceId);
    }

    public static TenantContext ofSystem(UUID tenantId, String traceId) {
        return new TenantContext(tenantId, null, Role.OWNER, ActorType.SYSTEM, null, traceId);
    }
}
