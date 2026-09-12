package com.resolveiq.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dependencies", uniqueConstraints = {
        @UniqueConstraint(name = "uq_dependencies_tenant_services", columnNames = {"tenant_id", "upstream_service_id", "downstream_service_id"})
})
public class DependencyEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upstream_service_id", nullable = false)
    private UUID upstreamServiceId;

    @Column(name = "downstream_service_id", nullable = false)
    private UUID downstreamServiceId;

    @Column(name = "call_type", nullable = false)
    private String callType = "HTTP";

    @Column(name = "health_status", nullable = false)
    private String healthStatus = "HEALTHY";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DependencyEntity() {
    }

    public DependencyEntity(UUID tenantId, UUID upstreamServiceId, UUID downstreamServiceId, String callType, String healthStatus) {
        super(tenantId);
        this.upstreamServiceId = upstreamServiceId;
        this.downstreamServiceId = downstreamServiceId;
        this.callType = callType != null ? callType : "HTTP";
        this.healthStatus = healthStatus != null ? healthStatus : "HEALTHY";
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUpstreamServiceId() {
        return upstreamServiceId;
    }

    public void setUpstreamServiceId(UUID upstreamServiceId) {
        this.upstreamServiceId = upstreamServiceId;
    }

    public UUID getDownstreamServiceId() {
        return downstreamServiceId;
    }

    public void setDownstreamServiceId(UUID downstreamServiceId) {
        this.downstreamServiceId = downstreamServiceId;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
