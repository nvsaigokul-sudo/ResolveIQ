package com.resolveiq.common.crypto;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Argon2id salted password and API-key hashing per PRD Section 12.1.
 */
public final class Argon2PasswordEncoderUtil {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KB = 16384; // 16 MB
    private static final int ITERATIONS = 3;

    private static final Argon2PasswordEncoder ENCODER = new Argon2PasswordEncoder(
            SALT_LENGTH,
            HASH_LENGTH,
            PARALLELISM,
            MEMORY_KB,
            ITERATIONS
    );

    private Argon2PasswordEncoderUtil() {
    }

    public static String hash(CharSequence rawSecret) {
        if (rawSecret == null || rawSecret.length() == 0) {
            throw new IllegalArgumentException("Secret to hash cannot be null or empty");
        }
        return ENCODER.encode(rawSecret);
    }

    public static boolean matches(CharSequence rawSecret, String encodedHash) {
        if (rawSecret == null || encodedHash == null) {
            return false;
        }
        return ENCODER.matches(rawSecret, encodedHash);
    }
}
