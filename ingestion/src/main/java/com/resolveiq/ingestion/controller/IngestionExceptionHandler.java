package com.resolveiq.ingestion.controller;

import com.resolveiq.common.dto.ErrorResponse;
import com.resolveiq.common.exception.UnauthorizedException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.ingestion.exception.PayloadOversizedException;
import com.resolveiq.ingestion.kafka.IngestionBackpressureException;
import com.resolveiq.ingestion.ratelimit.TenantRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Exception handler producing consistent error responses for Ingestion Service (PRD §35.2).
 */
@RestControllerAdvice
public class IngestionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IngestionExceptionHandler.class);

    private String getTraceId(HttpServletRequest request, HttpServletResponse response) {
        String traceId = response.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.isBlank()) return traceId;
        traceId = request.getHeader("X-Trace-Id");
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }

    @ExceptionHandler(TenantRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            TenantRateLimitExceededException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("TENANT_RATE_LIMIT_EXCEEDED", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(IngestionBackpressureException.class)
    public ResponseEntity<ErrorResponse> handleBackpressure(
            IngestionBackpressureException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("INGESTION_BACKPRESSURE", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(PayloadOversizedException.class)
    public ResponseEntity<ErrorResponse> handleOversized(
            PayloadOversizedException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("PAYLOAD_OVERSIZED", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            ValidationException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_FAILED", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        log.error("Unhandled ingestion exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "Internal ingestion failure", traceId));
    }
}
