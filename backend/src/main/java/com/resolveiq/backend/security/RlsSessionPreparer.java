package com.resolveiq.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Sets transaction-scoped app.tenant_id parameter on PostgreSQL connections (ADR-009).
 * Uses set_config('app.tenant_id', ?, true) so parameter resets automatically upon transaction completion.
 */
@Component
public class RlsSessionPreparer {

    private static final Logger log = LoggerFactory.getLogger(RlsSessionPreparer.class);

    public void setSessionTenantId(Connection connection, UUID tenantId) {
        if (connection == null || tenantId == null) {
            return;
        }

        try {
            String dbProduct = connection.getMetaData().getDatabaseProductName();
            if (dbProduct != null && dbProduct.toLowerCase().contains("postgresql")) {
                try (PreparedStatement stmt = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    stmt.setString(1, tenantId.toString());
                    stmt.execute();
                    log.debug("Set PostgreSQL app.tenant_id = {}", tenantId);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to set PostgreSQL RLS tenant context: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to bind tenant context to database connection", e);
        }
    }
}
