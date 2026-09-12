package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.ApiKeyEntity;
import com.resolveiq.backend.service.ApiKeyService;
import com.resolveiq.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    public record CreateApiKeyRequest(
            @NotBlank(message = "Name cannot be blank") String name,
            String scopes
    ) {}

    public record ApiKeyDto(
            UUID id,
            String name,
            String keyPrefix,
            String secretKey,
            String scopes,
            boolean isRevoked
    ) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiKeyDto> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyService.ApiKeyCreateResult result = apiKeyService.createApiKey(request.name(), request.scopes(), null);
        ApiKeyDto response = new ApiKeyDto(
                result.id(),
                result.name(),
                result.keyPrefix(),
                result.plaintextKey(),
                result.scopes(),
                false
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApiKeyDto>> listApiKeys() {
        List<ApiKeyDto> keys = apiKeyService.listApiKeysForCurrentTenant().stream()
                .map(k -> new ApiKeyDto(k.getId(), k.getName(), k.getKeyPrefix(), null, k.getScopes(), k.isRevoked()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ofItems(keys, null, (long) keys.size()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> revokeApiKey(@PathVariable("id") UUID id) {
        apiKeyService.revokeApiKey(id);
        return ResponseEntity.noContent().build();
    }
}
