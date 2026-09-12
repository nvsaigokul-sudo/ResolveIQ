package com.resolveiq.correlation.fingerprint;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Deterministic Incident Fingerprinter strictly conforming to PRD §19, §20.
 * Computes SHA-256 hash: hash(tenant_id:root_service:detector_type:error_signature).
 */
@Component
public class IncidentFingerprinter {

    public String computeFingerprint(
            UUID tenantId,
            String rootService,
            String detectorType,
            String errorSignature) {

        String tenantStr = tenantId != null ? tenantId.toString() : "global";
        String serviceStr = rootService != null ? rootService.trim().toLowerCase() : "unknown_service";
        String typeStr = detectorType != null ? detectorType.trim().toLowerCase() : "unknown_type";
        String errorStr = errorSignature != null ? errorSignature.trim().toLowerCase() : "generic_error";

        String canonical = String.format("%s:%s:%s:%s", tenantStr, serviceStr, typeStr, errorStr);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
