package com.resolveiq.detection.fingerprint;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Deterministic Anomaly Fingerprinting Engine (PRD §16, §17, §18).
 * Generates an immutable, collision-resistant SHA-256 fingerprint from:
 * hash(detector_id, tenant_id, service_id, metric_name, environment).
 */
@Component
public class AnomalyFingerprinter {

    public String computeFingerprint(String detectorId, UUID tenantId, String serviceId, String metricName, String environment) {
        String safeDetector = detectorId != null ? detectorId.trim() : "unknown_detector";
        String safeTenant = tenantId != null ? tenantId.toString() : "global";
        String safeService = serviceId != null ? serviceId.trim() : "unknown_service";
        String safeMetric = metricName != null ? metricName.trim() : "unknown_metric";
        String safeEnv = environment != null ? environment.trim().toLowerCase() : "production";

        String rawKey = safeDetector + ":" + safeTenant + ":" + safeService + ":" + safeMetric + ":" + safeEnv;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(rawKey.hashCode());
        }
    }
}
