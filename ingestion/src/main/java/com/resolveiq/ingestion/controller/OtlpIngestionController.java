package com.resolveiq.ingestion.controller;

import com.resolveiq.common.exception.UnauthorizedException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import com.resolveiq.ingestion.dedup.IdempotencyCache;
import com.resolveiq.ingestion.exception.PayloadOversizedException;
import com.resolveiq.ingestion.kafka.TelemetryKafkaProducer;
import com.resolveiq.ingestion.model.*;
import com.resolveiq.ingestion.ratelimit.TenantRateLimiter;
import com.resolveiq.ingestion.redaction.TelemetryRedactor;
import com.resolveiq.ingestion.security.IngestionCredentialValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * OpenTelemetry HTTP Ingestion Controller for Metrics, Logs, and Traces (PRD Section 13, 14, 15).
 * Endpoints: /v1/metrics, /v1/logs, /v1/traces
 */
@RestController
@RequestMapping("/v1")
public class OtlpIngestionController {

    private static final Logger log = LoggerFactory.getLogger(OtlpIngestionController.class);

    private final TelemetryKafkaProducer kafkaProducer;
    private final TelemetryRedactor redactor;
    private final TenantRateLimiter rateLimiter;
    private final IdempotencyCache idempotencyCache;
    private final IngestionCredentialValidator credentialValidator;
    private final long maxPayloadBytes;

    public OtlpIngestionController(
            TelemetryKafkaProducer kafkaProducer,
            TelemetryRedactor redactor,
            TenantRateLimiter rateLimiter,
            IdempotencyCache idempotencyCache,
            IngestionCredentialValidator credentialValidator,
            @Value("${resolveiq.ingestion.max-payload-bytes:1048576}") long maxPayloadBytes) {
        this.kafkaProducer = kafkaProducer;
        this.redactor = redactor;
        this.rateLimiter = rateLimiter;
        this.idempotencyCache = idempotencyCache;
        this.credentialValidator = credentialValidator;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    private TenantContext authenticateRequest(HttpServletRequest request, String traceId) {
        // Strict security: ignore client X-Tenant-Id header
        if (request.getHeader("X-Tenant-Id") != null) {
            log.debug("Client supplied X-Tenant-Id; ignoring header per PRD §11.2");
        }

        String apiKey = request.getHeader("X-API-Key");
        String authHeader = request.getHeader("Authorization");

        return credentialValidator.authenticate(apiKey, authHeader, traceId)
                .orElseThrow(() -> new UnauthorizedException("Authentication required: missing or invalid credentials"));
    }

    private String getTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }

    @PostMapping("/metrics")
    public ResponseEntity<IngestionResponse> ingestMetrics(
            @RequestBody OtlpMetricsBatch batch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request);
        response.setHeader("X-Trace-Id", traceId);

        TenantContext tenantCtx = authenticateRequest(request, traceId);

        UUID eventId = batch.eventId() != null ? batch.eventId() :
                (idempotencyKey != null ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes()) : UUID.randomUUID());

        // 1. Idempotency dedup check
        if (!idempotencyCache.recordIfUnique(eventId)) {
            log.info("Duplicate metrics event_id [{}] dropped at ingestion dedup cache", eventId);
            return ResponseEntity.ok(IngestionResponse.deduplicated(eventId, traceId));
        }

        List<MetricObservedPayload> metrics = batch.metrics();
        if (metrics == null || metrics.isEmpty()) {
            throw new ValidationException("Metrics batch cannot be empty");
        }

        // 2. Token-bucket rate limiting
        TenantRateLimiter.RateLimitResult rateResult = rateLimiter.tryConsume(tenantCtx.tenantId(), metrics.size());
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateResult.remainingTokens()));
        if (!rateResult.isAllowed()) {
            response.setHeader("Retry-After", String.valueOf(rateResult.retryAfterSeconds()));
            throw new com.resolveiq.ingestion.ratelimit.TenantRateLimitExceededException(
                    "Tenant rate limit exceeded. Please back off.",
                    rateResult.retryAfterSeconds()
            );
        }

        // 3. Normalization, Defense-in-depth Redaction, and Kafka Produce
        String serviceName = batch.serviceName() != null ? batch.serviceName() : "unknown-service";
        for (MetricObservedPayload rawMetric : metrics) {
            MetricObservedPayload redactedMetric = redactor.redactMetric(rawMetric);
            KafkaEventEnvelope<MetricObservedPayload> envelope = KafkaEventEnvelope.create(
                    tenantCtx.tenantId(),
                    null,
                    batch.environment(),
                    traceId,
                    null,
                    redactedMetric
            );
            kafkaProducer.send(TelemetryKafkaProducer.TOPIC_METRICS, serviceName, envelope);
        }

        return ResponseEntity.ok(IngestionResponse.success(eventId, metrics.size(), traceId));
    }

    @PostMapping("/logs")
    public ResponseEntity<IngestionResponse> ingestLogs(
            @RequestBody OtlpLogsBatch batch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request);
        response.setHeader("X-Trace-Id", traceId);

        TenantContext tenantCtx = authenticateRequest(request, traceId);

        UUID eventId = batch.eventId() != null ? batch.eventId() :
                (idempotencyKey != null ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes()) : UUID.randomUUID());

        // 1. Idempotency dedup check
        if (!idempotencyCache.recordIfUnique(eventId)) {
            log.info("Duplicate logs event_id [{}] dropped at ingestion dedup cache", eventId);
            return ResponseEntity.ok(IngestionResponse.deduplicated(eventId, traceId));
        }

        List<LogObservedPayload> logs = batch.logs();
        if (logs == null || logs.isEmpty()) {
            throw new ValidationException("Logs batch cannot be empty");
        }

        // 2. Token-bucket rate limiting
        TenantRateLimiter.RateLimitResult rateResult = rateLimiter.tryConsume(tenantCtx.tenantId(), logs.size());
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateResult.remainingTokens()));
        if (!rateResult.isAllowed()) {
            response.setHeader("Retry-After", String.valueOf(rateResult.retryAfterSeconds()));
            throw new com.resolveiq.ingestion.ratelimit.TenantRateLimitExceededException(
                    "Tenant rate limit exceeded. Please back off.",
                    rateResult.retryAfterSeconds()
            );
        }

        // 3. Normalization, Redaction, and Kafka Produce
        String serviceName = batch.serviceName() != null ? batch.serviceName() : "unknown-service";
        for (LogObservedPayload rawLog : logs) {
            LogObservedPayload redactedLog = redactor.redactLog(rawLog);
            KafkaEventEnvelope<LogObservedPayload> envelope = KafkaEventEnvelope.create(
                    tenantCtx.tenantId(),
                    null,
                    batch.environment(),
                    traceId,
                    redactedLog.traceId(),
                    redactedLog
            );
            kafkaProducer.send(TelemetryKafkaProducer.TOPIC_LOGS, serviceName, envelope);
        }

        return ResponseEntity.ok(IngestionResponse.success(eventId, logs.size(), traceId));
    }

    @PostMapping("/traces")
    public ResponseEntity<IngestionResponse> ingestTraces(
            @RequestBody OtlpTracesBatch batch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {

        String traceId = getTraceId(request);
        response.setHeader("X-Trace-Id", traceId);

        TenantContext tenantCtx = authenticateRequest(request, traceId);

        UUID eventId = batch.eventId() != null ? batch.eventId() :
                (idempotencyKey != null ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes()) : UUID.randomUUID());

        // 1. Idempotency dedup check
        if (!idempotencyCache.recordIfUnique(eventId)) {
            log.info("Duplicate traces event_id [{}] dropped at ingestion dedup cache", eventId);
            return ResponseEntity.ok(IngestionResponse.deduplicated(eventId, traceId));
        }

        List<TraceObservedPayload> spans = batch.spans();
        if (spans == null || spans.isEmpty()) {
            throw new ValidationException("Traces batch cannot be empty");
        }

        // 2. Token-bucket rate limiting
        TenantRateLimiter.RateLimitResult rateResult = rateLimiter.tryConsume(tenantCtx.tenantId(), spans.size());
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateResult.remainingTokens()));
        if (!rateResult.isAllowed()) {
            response.setHeader("Retry-After", String.valueOf(rateResult.retryAfterSeconds()));
            throw new com.resolveiq.ingestion.ratelimit.TenantRateLimitExceededException(
                    "Tenant rate limit exceeded. Please back off.",
                    rateResult.retryAfterSeconds()
            );
        }

        // 3. Normalization, Redaction, and Kafka Produce
        String serviceName = batch.serviceName() != null ? batch.serviceName() : "unknown-service";
        for (TraceObservedPayload rawSpan : spans) {
            TraceObservedPayload redactedSpan = redactor.redactTrace(rawSpan);
            KafkaEventEnvelope<TraceObservedPayload> envelope = KafkaEventEnvelope.create(
                    tenantCtx.tenantId(),
                    null,
                    batch.environment(),
                    traceId,
                    redactedSpan.traceId(),
                    redactedSpan
            );
            kafkaProducer.send(TelemetryKafkaProducer.TOPIC_TRACES, serviceName, envelope);
        }

        return ResponseEntity.ok(IngestionResponse.success(eventId, spans.size(), traceId));
    }
}
