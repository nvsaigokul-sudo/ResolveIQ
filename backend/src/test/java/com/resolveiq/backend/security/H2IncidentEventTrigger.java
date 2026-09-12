package com.resolveiq.backend.security;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * H2 database trigger enforcing database-level immutability on incident_events for embedded test runs (ADR-008, PRD §20).
 */
public class H2IncidentEventTrigger implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("Incident timeline events are strictly immutable: UPDATE and DELETE operations are prohibited at database level");
    }

    @Override
    public void close() {
    }

    @Override
    public void remove() {
    }
}
