package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.ApiKeyEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.repository.ApiKeyRepository;
import com.resolveiq.backend.service.ApiKeyService;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.crypto.ApiKeyGenerator;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationEntity tenant;

    @BeforeEach
    void setUp() {
        String slug = "apikey-org-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("API Key Corp", slug, "ENTERPRISE");
    }

    @Test
    @DisplayName("Acceptance Criterion 4: API keys use riq_live_ prefix, Argon2id hashing, and immediate revocation")
    void testApiKeyLifecycleAndRevocation() throws Exception {
        UUID adminId = UUID.randomUUID();
        TenantContext adminCtx = TenantContext.ofUser(tenant.getId(), adminId, Role.ADMIN, "trace-key-setup");

        // 1. Create API key under Admin context
        TenantContextHolder.setContext(adminCtx);
        ApiKeyService.ApiKeyCreateResult result;
        try {
            result = apiKeyService.createApiKey("collector-key", "METRICS_WRITE", null);
        } finally {
            TenantContextHolder.clear();
        }

        // Assert Prefix and format
        assertThat(result.plaintextKey()).startsWith("riq_live_");
        assertThat(result.keyPrefix()).startsWith("riq_live_");

        // Assert that in the database, the secret is stored ONLY as a salted Argon2id hash (never plaintext)
        ApiKeyEntity storedEntity = apiKeyRepository.findById(result.id()).orElseThrow();
        assertThat(storedEntity.getHashedSecret()).startsWith("$argon2id$");
        assertThat(storedEntity.getHashedSecret()).isNotEqualTo(result.plaintextKey());
        assertThat(ApiKeyGenerator.verify(result.plaintextKey(), storedEntity.getHashedSecret())).isTrue();

        // 2. Perform authenticated request with the valid API key
        mockMvc.perform(get("/api/v1/incidents")
                        .header("X-API-Key", result.plaintextKey())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 3. Immediately revoke the API key
        TenantContextHolder.setContext(adminCtx);
        try {
            apiKeyService.revokeApiKey(result.id());
        } finally {
            TenantContextHolder.clear();
        }

        // 4. Immediately attempt request with the revoked API key (must be rejected with 401 within <=5s target)
        mockMvc.perform(get("/api/v1/incidents")
                        .header("X-API-Key", result.plaintextKey())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").exists());
    }
}
