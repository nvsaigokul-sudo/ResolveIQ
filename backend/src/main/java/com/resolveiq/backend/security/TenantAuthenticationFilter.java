package com.resolveiq.backend.security;

import com.resolveiq.backend.service.ApiKeyService;
import com.resolveiq.common.security.ActorType;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter responsible for tenant identity resolution and header-tampering immunity (PRD Section 11.2).
 * Strictly resolves identity from cryptographic JWT or hashed API key.
 * Any client-supplied X-Tenant-Id header is completely ignored.
 */
@Component
public class TenantAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantAuthenticationFilter.class);

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String CLIENT_TENANT_HEADER = "X-Tenant-Id";

    private final JwtTokenUtil jwtTokenUtil;
    private final ApiKeyService apiKeyService;

    public TenantAuthenticationFilter(JwtTokenUtil jwtTokenUtil, ApiKeyService apiKeyService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.setHeader(TRACE_HEADER, traceId);

        // Security check: Ignore client-supplied X-Tenant-Id header completely
        if (request.getHeader(CLIENT_TENANT_HEADER) != null) {
            log.debug("Client supplied X-Tenant-Id header [{}]; ignoring header per PRD Section 11.2",
                    request.getHeader(CLIENT_TENANT_HEADER));
        }

        try {
            Optional<TenantContext> resolvedContext = resolveTenantContext(request, traceId);
            if (resolvedContext.isPresent()) {
                TenantContext context = resolvedContext.get();
                TenantContextHolder.setContext(context);
                TenantAuthenticationToken auth = new TenantAuthenticationToken(context);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<TenantContext> resolveTenantContext(HttpServletRequest request, String traceId) {
        // 1. Check Bearer JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                JwtTokenUtil.TokenClaims claims = jwtTokenUtil.parseAndValidateToken(jwt);
                TenantContext context = new TenantContext(
                        claims.tenantId(),
                        claims.userId(),
                        claims.role(),
                        ActorType.USER,
                        null,
                        traceId
                );
                return Optional.of(context);
            } catch (Exception e) {
                log.warn("Invalid JWT token presented: {}", e.getMessage());
                return Optional.empty();
            }
        }

        // 2. Check X-API-Key header
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKeyService.validateApiKey(apiKey, traceId);
        }

        return Optional.empty();
    }
}
