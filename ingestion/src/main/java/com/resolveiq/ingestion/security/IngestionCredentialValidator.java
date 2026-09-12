package com.resolveiq.ingestion.security;

import com.resolveiq.common.crypto.ApiKeyGenerator;
import com.resolveiq.common.security.ActorType;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates credentials at the Ingestion Service boundary (PRD Section 11.2, 14, 15).
 * Resolves tenant strictly from cryptographic JWT or registered/hashed API keys.
 */
@Component
public class IngestionCredentialValidator {

    private static final Logger log = LoggerFactory.getLogger(IngestionCredentialValidator.class);

    private final JwtTokenUtil jwtTokenUtil;
    private final Map<String, ProvisionedCredential> registeredKeys = new ConcurrentHashMap<>();

    public record ProvisionedCredential(
            UUID tenantId,
            UUID apiKeyId,
            String hashedSecret,
            Role role
    ) {}

    public IngestionCredentialValidator(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    public void registerApiKey(String fullKey, UUID tenantId, UUID apiKeyId, String hashedSecret, Role role) {
        registeredKeys.put(fullKey, new ProvisionedCredential(tenantId, apiKeyId, hashedSecret, role));
    }

    public Optional<TenantContext> authenticate(String apiKeyHeader, String authorizationHeader, String traceId) {
        // 1. Validate Bearer JWT Token
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String jwt = authorizationHeader.substring(7);
            try {
                JwtTokenUtil.TokenClaims claims = jwtTokenUtil.parseAndValidateToken(jwt);
                return Optional.of(new TenantContext(
                        claims.tenantId(),
                        claims.userId(),
                        claims.role(),
                        ActorType.USER,
                        null,
                        traceId
                ));
            } catch (Exception e) {
                log.warn("Invalid JWT token at ingestion boundary: {}", e.getMessage());
                return Optional.empty();
            }
        }

        // 2. Validate API Key
        if (apiKeyHeader != null && apiKeyHeader.startsWith(ApiKeyGenerator.LIVE_PREFIX)) {
            ProvisionedCredential cred = registeredKeys.get(apiKeyHeader);
            if (cred != null) {
                if (cred.hashedSecret() == null || ApiKeyGenerator.verify(apiKeyHeader, cred.hashedSecret())) {
                    return Optional.of(new TenantContext(
                            cred.tenantId(),
                            null,
                            cred.role() != null ? cred.role() : Role.ADMIN,
                            ActorType.COLLECTOR,
                            cred.apiKeyId(),
                            traceId
                    ));
                }
            }
        }

        return Optional.empty();
    }
}
