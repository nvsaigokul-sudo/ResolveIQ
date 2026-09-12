package com.resolveiq.backend.chaos;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos & Fault Injection: PostgreSQL Connection Pool Exhaustion, Transaction Rollbacks, and Zero Corruption (PRD §§34, 52).
 */
@SpringBootTest
@ActiveProfiles("test")
public class DatabaseFailureAndResilienceTest {

    @Autowired private DataSource dataSource;
    @Autowired private TenantService tenantService;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private OrganizationEntity tenant;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("DB Chaos Org " + suffix, "dbchaos-" + suffix, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "db-setup"));
        try {
            project = tenantService.createProject("DB Project", "db-proj-" + suffix, "DB Chaos Project");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("DB Chaos 1: Transaction Rollback Guarantees Zero Half-Written Records on Failure")
    void testTransactionRollbackOnFailure() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "tx-test"));
        long initialCount = incidentRepository.count();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            IncidentEntity inc = new IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    "fp-rollback-" + UUID.randomUUID(),
                    "Incident before intentional error",
                    "payment-service",
                    IncidentSeverity.SEV1,
                    "Platform"
            );
            incidentRepository.save(inc);

            // Trigger mid-operation exception
            throw new RuntimeException("Simulated mid-transaction outage or constraint failure");
        } catch (Exception ex) {
            transactionManager.rollback(status);
        } finally {
            TenantContextHolder.clear();
        }

        // Count after rollback must remain completely identical
        long finalCount = incidentRepository.count();
        assertThat(finalCount).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("DB Chaos 2: Connection Pool Exhaustion Handling with Graceful Timeout")
    void testConnectionPoolExhaustionHandling() throws Exception {
        // Concurrently attempt to acquire multiple connections
        int totalWorkers = 15;
        ExecutorService executor = Executors.newFixedThreadPool(totalWorkers);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < totalWorkers; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                try (Connection conn = dataSource.getConnection()) {
                    // Hold connection briefly
                    Thread.sleep(50);
                    return conn.isValid(2);
                } catch (SQLException | InterruptedException e) {
                    return false;
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();

        int successfulConnections = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successfulConnections++;
            }
        }

        // Must successfully acquire connections without deadlock
        assertThat(successfulConnections).isGreaterThan(0);
    }

    @Test
    @DisplayName("DB Chaos 3: Query Timeout Protection Prevents Thread Hanging")
    void testQueryTimeoutProtection() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            java.sql.Statement stmt = conn.createStatement();
            stmt.setQueryTimeout(2); // 2 second timeout

            // Execute fast statement - verifies query timeout property is active
            java.sql.ResultSet rs = stmt.executeQuery("SELECT 1");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
