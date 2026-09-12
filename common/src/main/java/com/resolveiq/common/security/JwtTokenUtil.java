package com.resolveiq.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token issuing and verification supporting short-lived access tokens (~15 mins)
 * carrying tenant_id, user_id, and role claims (PRD Section 11.2, 12.1).
 */
public class JwtTokenUtil {

    private final SecretKey signingKey;
    private final String issuer;
    private final long expirationMinutes;

    public JwtTokenUtil(String secret, String issuer, long expirationMinutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    public record TokenClaims(
            UUID tenantId,
            UUID userId,
            String email,
            Role role
    ) {}

    public String generateAccessToken(UUID tenantId, UUID userId, String email, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public TokenClaims parseAndValidateToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        Role role = Role.valueOf(claims.get("role", String.class));

        return new TokenClaims(tenantId, userId, email, role);
    }
}
