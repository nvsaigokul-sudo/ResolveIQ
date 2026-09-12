package com.resolveiq.common.security;

import java.util.Set;

/**
 * RBAC Roles defined in PRD Section 12.2.
 */
public enum Role {
    OWNER(Set.of(
            "billing:manage",
            "org:delete",
            "org:manage",
            "users:manage",
            "keys:manage",
            "integrations:manage",
            "rules:manage",
            "incidents:manage",
            "investigation:trigger",
            "incidents:read"
    )),
    ADMIN(Set.of(
            "org:manage",
            "users:manage",
            "keys:manage",
            "integrations:manage",
            "rules:manage",
            "incidents:manage",
            "investigation:trigger",
            "incidents:read"
    )),
    INCIDENT_MANAGER(Set.of(
            "incidents:manage",
            "investigation:trigger",
            "incidents:read"
    )),
    SRE(Set.of(
            "incidents:manage",
            "rules:manage",
            "investigation:trigger",
            "incidents:read"
    )),
    DEVELOPER(Set.of(
            "incidents:comment",
            "incidents:read"
    )),
    VIEWER(Set.of(
            "incidents:read"
    ));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean canManageBilling() {
        return hasPermission("billing:manage");
    }

    public boolean canManageOrg() {
        return hasPermission("org:manage");
    }

    public boolean canManageUsers() {
        return hasPermission("users:manage");
    }

    public boolean canManageApiKeys() {
        return hasPermission("keys:manage");
    }

    public boolean canManageDetectionRules() {
        return hasPermission("rules:manage");
    }

    public boolean canTriggerInvestigation() {
        return hasPermission("investigation:trigger");
    }

    public boolean canModifyIncidentState() {
        return hasPermission("incidents:manage");
    }

    public boolean isReadOnly() {
        return this == VIEWER;
    }
}
