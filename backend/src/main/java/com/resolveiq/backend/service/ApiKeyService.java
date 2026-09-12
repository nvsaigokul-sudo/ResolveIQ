package com.resolveiq.backend.service;

import com.resolveiq.backend.domain.ApiKeyEntity;
import com.resolveiq.backend.repository.ApiKeyRepository;
import com.resolveiq.common.crypto.ApiKeyGenerator;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.security.ActorType;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing API keys with Argon2id hashing and immediate revocation (PRD Section 12.1).
 */
@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogService auditLogService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, AuditLogService auditLogService) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditLogService = auditLogService;
    }

    public record ApiKeyCreateResult(
            UUID id,
            String name,
            String keyPrefix,
            String plaintextKey,
            String scopes,
            Instant createdAt
    ) {}

    @Transactional
    public ApiKeyCreateResult createApiKey(String name, String scopes, Instant expiresAt) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        if (ctx.role() != Role.OWNER && ctx.role() != Role.ADMIN) {
            throw new ForbiddenException("Only Organization Owner or Admin can create API keys");
        }

        ApiKeyGenerator.GeneratedApiKey generated = ApiKeyGenerator.generate();

        ApiKeyEntity entity = new ApiKeyEntity(
                ctx.tenantId(),
                name,
                generated.keyPrefix(),
                generated.hashedSecret(),
                scopes != null ? scopes : "ALL",
                expiresAt
        );

        ApiKeyEntity saved = apiKeyRepository.save(entity);

        auditLogService.record(
                ctx.tenantId(),
                ctx.userId(),
                ctx.actorType().name(),
                "API_KEY_CREATED",
                "api_key:" + saved.getId(),
                null,
                "created: " + name + " (prefix: " + saved.getKeyPrefix() + ")",
                null,
                ctx.traceId()
        );

        return new ApiKeyCreateResult(
                saved.getId(),
                saved.getName(),
                saved.getKeyPrefix(),
                generated.fullKey(),
                saved.getScopes(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<TenantContext> validateApiKey(String rawKey, String traceId) {
        if (rawKey == null || !rawKey.startsWith(ApiKeyGenerator.LIVE_PREFIX)) {
            return Optional.empty();
        }

        String prefix;
        try {
            prefix = ApiKeyGenerator.extractPrefix(rawKey);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        List<ApiKeyEntity> candidates = apiKeyRepository.findAllByKeyPrefixAndRevokedAtIsNull(prefix);
        for (ApiKeyEntity candidate : candidates) {
            if (candidate.isValid() && ApiKeyGenerator.verify(rawKey, candidate.getHashedSecret())) {
                TenantContext context = new TenantContext(
                        candidate.getTenantId(),
                        null,
                        Role.ADMIN, // API keys default to Admin programmatic scope
                        ActorType.API_KEY,
                        candidate.getId(),
                        traceId
                );
                return Optional.of(context);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void revokeApiKey(UUID apiKeyId) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        if (ctx.role() != Role.OWNER && ctx.role() != Role.ADMIN) {
            throw new ForbiddenException("Only Organization Owner or Admin can revoke API keys");
        }

        ApiKeyEntity entity = apiKeyRepository.findByIdAndTenantId(apiKeyId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + apiKeyId));

        entity.setRevokedAt(Instant.now());
        apiKeyRepository.save(entity);

        auditLogService.record(
                ctx.tenantId(),
                ctx.userId(),
                ctx.actorType().name(),
                "API_KEY_REVOKED",
                "api_key:" + entity.getId(),
                "active",
                "revoked",
                null,
                ctx.traceId()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyEntity> listApiKeysForCurrentTenant() {
        return apiKeyRepository.findAllForCurrentTenant();
    }
}
