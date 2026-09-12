package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ErrorResponse;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final TenantAuthenticationFilter tenantAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public SecurityConfig(TenantAuthenticationFilter tenantAuthenticationFilter,
                          ObjectMapper objectMapper,
                          AuditLogService auditLogService) {
        this.tenantAuthenticationFilter = tenantAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterBefore(tenantAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Trace-Id", "X-API-Key", "X-Requested-With", "Accept", "Origin"));
        config.setExposedHeaders(Arrays.asList("X-Trace-Id", "Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            String traceId = response.getHeader(TenantAuthenticationFilter.TRACE_HEADER);
            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
            }

            log.warn("Authentication failed for request {} {}: {}", request.getMethod(), request.getRequestURI(), authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse error = ErrorResponse.of(
                    "UNAUTHORIZED",
                    "Authentication required: invalid or missing credentials",
                    traceId
            );
            objectMapper.writeValue(response.getOutputStream(), error);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String traceId = response.getHeader(TenantAuthenticationFilter.TRACE_HEADER);
            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
            }

            TenantContext ctx = TenantContextHolder.getContext().orElse(null);
            log.warn("Access denied for user {} (role {}) attempting {} {}: {}",
                    ctx != null ? ctx.userId() : "anonymous",
                    ctx != null ? ctx.role() : "none",
                    request.getMethod(),
                    request.getRequestURI(),
                    accessDeniedException.getMessage());

            // Record unauthorized attempt in audit log per PRD Section 12.3
            if (ctx != null) {
                try {
                    auditLogService.record(
                            ctx.tenantId(),
                            ctx.userId() != null ? ctx.userId() : ctx.apiKeyId(),
                            ctx.actorType().name(),
                            "UNAUTHORIZED_ACCESS_ATTEMPT",
                            request.getMethod() + " " + request.getRequestURI(),
                            null,
                            "Denied role " + ctx.role() + ": " + accessDeniedException.getMessage(),
                            request.getRemoteAddr(),
                            traceId
                    );
                } catch (Exception e) {
                    log.error("Failed to record access denial in audit log: {}", e.getMessage());
                }
            }

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse error = ErrorResponse.of(
                    "FORBIDDEN",
                    "Access denied: principal lacks required permission for this action",
                    traceId
            );
            objectMapper.writeValue(response.getOutputStream(), error);
        };
    }
}
