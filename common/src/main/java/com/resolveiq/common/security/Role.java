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
            "incidents:read",
            "knowledge:manage",
            "knowledge:read"
    )),
    ADMIN(Set.of(
            "org:manage",
            "users:manage",
            "keys:manage",
            "integrations:manage",
            "rules:manage",
            "incidents:manage",
            "investigation:trigger",
            "incidents:read",
            "knowledge:manage",
            "knowledge:read"
    )),
    INCIDENT_MANAGER(Set.of(
            "incidents:manage",
            "investigation:trigger",
            "incidents:read",
            "knowledge:manage",
            "knowledge:read"
    )),
    SRE(Set.of(
            "incidents:manage",
            "rules:manage",
            "investigation:trigger",
            "incidents:read",
            "knowledge:manage",
            "knowledge:read"
    )),
    DEVELOPER(Set.of(
            "incidents:comment",
            "incidents:read",
            "knowledge:read"
    )),
    VIEWER(Set.of(
            "incidents:read",
            "knowledge:read"
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

    public boolean canManageKnowledge() {
        return hasPermission("knowledge:manage");
    }

    public boolean canReadKnowledge() {
        return hasPermission("knowledge:read");
    }

    public boolean isReadOnly() {
        return this == VIEWER;
    }
}
