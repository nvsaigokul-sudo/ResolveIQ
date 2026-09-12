package com.resolveiq.backend.api;

import com.resolveiq.backend.exception.InvalidLifecycleTransitionException;
import com.resolveiq.backend.security.TenantAuthenticationFilter;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ErrorResponse;
import com.resolveiq.common.exception.*;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized exception handling producing consistent error schema per PRD Section 35.2.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditLogService auditLogService;

    public GlobalExceptionHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private String getTraceId(HttpServletRequest request, HttpServletResponse response) {
        String traceId = response.getHeader(TenantAuthenticationFilter.TRACE_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        traceId = request.getHeader(TenantAuthenticationFilter.TRACE_HEADER);
        return traceId != null && !traceId.isBlank() ? traceId : UUID.randomUUID().toString();
    }

    @ExceptionHandler(TenantScopeViolationException.class)
    public ResponseEntity<ErrorResponse> handleTenantScopeViolation(TenantScopeViolationException ex,
                                                                    HttpServletRequest request,
                                                                    HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        log.warn("Tenant scope violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND) // Return 404 per PRD §35.3 to avoid confirming existence
                .body(ErrorResponse.of("TENANT_SCOPE_VIOLATION", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex,
                                                           HttpServletRequest request,
                                                           HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(AccessDeniedException ex,
                                                                 HttpServletRequest request,
                                                                 HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        TenantContext ctx = TenantContextHolder.getContext().orElse(null);
        if (ctx != null) {
            try {
                auditLogService.record(
                        ctx.tenantId(),
                        ctx.userId() != null ? ctx.userId() : ctx.apiKeyId(),
                        ctx.actorType().name(),
                        "UNAUTHORIZED_ACCESS_ATTEMPT",
                        request.getMethod() + " " + request.getRequestURI(),
                        null,
                        "Denied role " + ctx.role() + ": " + ex.getMessage(),
                        request.getRemoteAddr(),
                        traceId
                );
            } catch (Exception e) {
                log.error("Failed to record access denial in audit log: {}", e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "Access denied: insufficient permissions", traceId));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage(), traceId));
    }

    @ExceptionHandler(InvalidLifecycleTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLifecycleTransition(InvalidLifecycleTransitionException ex,
                                                                           HttpServletRequest request,
                                                                           HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        Map<String, Object> details = Map.of(
                "fromStatus", ex.getFromStatus() != null ? ex.getFromStatus().name() : "NULL",
                "toStatus", ex.getToStatus() != null ? ex.getToStatus().name() : "NULL"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_LIFECYCLE_TRANSITION", ex.getMessage(), traceId, details));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_FAILED", ex.getMessage(), traceId, ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request,
                                                                      HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> fieldErrors.put(f.getField(), f.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_FAILED", "Input validation failed", traceId, fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex,
                                                                HttpServletRequest request,
                                                                HttpServletResponse response) {
        String traceId = getTraceId(request, response);
        log.error("Unhandled exception on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "An unexpected internal server error occurred", traceId));
    }
}
