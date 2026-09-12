package com.resolveiq.backend.config;

import com.resolveiq.common.security.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public JwtTokenUtil jwtTokenUtil(
            @Value("${resolveiq.jwt.secret:default-secret-key-for-resolveiq-system-minimum-32-chars-long}") String secret,
            @Value("${resolveiq.jwt.issuer:resolveiq-auth}") String issuer,
            @Value("${resolveiq.jwt.expiration-minutes:15}") long expirationMinutes) {
        return new JwtTokenUtil(secret, issuer, expirationMinutes);
    }
}
