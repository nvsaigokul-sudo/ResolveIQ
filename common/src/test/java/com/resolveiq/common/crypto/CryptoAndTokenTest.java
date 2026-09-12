package com.resolveiq.common.crypto;

import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoAndTokenTest {

    @Test
    @DisplayName("Argon2id hashes and verifies secrets correctly")
    void testArgon2Hashing() {
        String secret = "super-secret-password-12345";
        String hash = Argon2PasswordEncoderUtil.hash(secret);

        assertThat(hash).isNotBlank();
        assertThat(hash).startsWith("$argon2id$");
        assertThat(Argon2PasswordEncoderUtil.matches(secret, hash)).isTrue();
        assertThat(Argon2PasswordEncoderUtil.matches("wrong-password", hash)).isFalse();
    }

    @Test
    @DisplayName("ApiKeyGenerator generates valid riq_live_ prefixed keys and hashes with Argon2id")
    void testApiKeyGeneration() {
        ApiKeyGenerator.GeneratedApiKey apiKey = ApiKeyGenerator.generate();

        assertThat(apiKey.fullKey()).startsWith("riq_live_");
        assertThat(apiKey.keyPrefix()).startsWith("riq_live_");
        assertThat(apiKey.hashedSecret()).startsWith("$argon2id$");

        assertThat(ApiKeyGenerator.verify(apiKey.fullKey(), apiKey.hashedSecret())).isTrue();
        assertThat(ApiKeyGenerator.verify("riq_live_invalid_secret_value", apiKey.hashedSecret())).isFalse();

        String extractedPrefix = ApiKeyGenerator.extractPrefix(apiKey.fullKey());
        assertThat(extractedPrefix).isEqualTo(apiKey.keyPrefix());
    }

    @Test
    @DisplayName("JwtTokenUtil generates and parses tenant-scoped claims correctly")
    void testJwtTokenGeneration() {
        String secret = "this-is-a-secure-256-bit-secret-key-for-resolveiq-testing";
        JwtTokenUtil jwtUtil = new JwtTokenUtil(secret, "resolveiq-test", 15);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "sre.priya@example.com";
        Role role = Role.SRE;

        String token = jwtUtil.generateAccessToken(tenantId, userId, email, role);
        assertThat(token).isNotBlank();

        JwtTokenUtil.TokenClaims claims = jwtUtil.parseAndValidateToken(token);
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo(email);
        assertThat(claims.role()).isEqualTo(Role.SRE);
    }

    @Test
    @DisplayName("Role permissions conform to PRD 12.2 RBAC matrix")
    void testRolePermissions() {
        assertThat(Role.OWNER.canManageBilling()).isTrue();
        assertThat(Role.ADMIN.canManageBilling()).isFalse();

        assertThat(Role.ADMIN.canManageUsers()).isTrue();
        assertThat(Role.INCIDENT_MANAGER.canManageUsers()).isFalse();

        assertThat(Role.SRE.canTriggerInvestigation()).isTrue();
        assertThat(Role.DEVELOPER.canTriggerInvestigation()).isFalse();

        assertThat(Role.VIEWER.isReadOnly()).isTrue();
        assertThat(Role.VIEWER.canModifyIncidentState()).isFalse();
    }
}
