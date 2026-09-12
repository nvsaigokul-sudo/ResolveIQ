package com.resolveiq.common.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates and validates API keys conforming to PRD Section 12.1:
 * Prefix: riq_live_
 * Secure random secret (256-bit entropy).
 */
public final class ApiKeyGenerator {

    public static final String LIVE_PREFIX = "riq_live_";
    private static final int RANDOM_BYTES_COUNT = 32; // 256 bits
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ApiKeyGenerator() {
    }

    public record GeneratedApiKey(
            String fullKey,
            String keyPrefix,
            String hashedSecret
    ) {}

    public static GeneratedApiKey generate() {
        byte[] randomBytes = new byte[RANDOM_BYTES_COUNT];
        SECURE_RANDOM.nextBytes(randomBytes);
        String secretPart = URL_ENCODER.encodeToString(randomBytes);

        String fullKey = LIVE_PREFIX + secretPart;
        // Prefix used for database indexing and fast candidate lookup (riq_live_ + first 8 chars)
        String keyPrefix = fullKey.substring(0, LIVE_PREFIX.length() + 8);
        String hashedSecret = Argon2PasswordEncoderUtil.hash(fullKey);

        return new GeneratedApiKey(fullKey, keyPrefix, hashedSecret);
    }

    public static String extractPrefix(String fullKey) {
        Objects.requireNonNull(fullKey, "API key cannot be null");
        if (!fullKey.startsWith(LIVE_PREFIX) || fullKey.length() < LIVE_PREFIX.length() + 8) {
            throw new IllegalArgumentException("Invalid API key format");
        }
        return fullKey.substring(0, LIVE_PREFIX.length() + 8);
    }

    public static boolean verify(String rawKey, String storedHashedSecret) {
        return Argon2PasswordEncoderUtil.matches(rawKey, storedHashedSecret);
    }
}
