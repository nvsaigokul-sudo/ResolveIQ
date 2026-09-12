package com.resolveiq.backend.security;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * H2 database trigger enforcing database-level immutability on audit_logs for embedded test runs (ADR-008).
 */
public class H2AuditLogTrigger implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("Audit logs are strictly immutable: UPDATE and DELETE operations are prohibited at database level");
    }

    @Override
    public void close() {
    }

    @Override
    public void remove() {
    }
}
