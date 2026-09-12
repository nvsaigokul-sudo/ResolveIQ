package com.resolveiq.backend.security;

import com.resolveiq.backend.domain.AuditLogEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.backend.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuditLogImmutabilityTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrganizationEntity tenant;

    @BeforeEach
    void setUp() {
        String slug = "audit-org-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Audit Immutability Org", slug, "ENTERPRISE");
    }

    @Test
    @DisplayName("Acceptance Criterion 5: Audit logs are strictly immutable at database engine level (UPDATE and DELETE prohibited)")
    void testAuditLogImmutability() {
        UUID actorId = UUID.randomUUID();

        // 1. Insert audit log record
        AuditLogEntity entry = auditLogService.record(
                tenant.getId(),
                actorId,
                "USER",
                "SECURITY_CONFIG_MODIFIED",
                "org:" + tenant.getId(),
                "tier:STANDARD",
                "tier:ENTERPRISE",
                "127.0.0.1",
                "trace-immutability"
        );

        assertThat(entry.getId()).isNotNull();

        // 2. Attempt direct SQL UPDATE on the audit log entry
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "UPDATE audit_logs SET action = 'TAMPERED_ACTION' WHERE id = ?",
                    entry.getId()
            );
        }).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Audit logs are strictly immutable");

        // 3. Attempt direct SQL DELETE on the audit log entry
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "DELETE FROM audit_logs WHERE id = ?",
                    entry.getId()
            );
        }).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Audit logs are strictly immutable");

        // 4. Verify original record remains completely untouched
        String currentAction = jdbcTemplate.queryForObject(
                "SELECT action FROM audit_logs WHERE id = ?",
                String.class,
                entry.getId()
        );
        assertThat(currentAction).isEqualTo("SECURITY_CONFIG_MODIFIED");
    }
}
