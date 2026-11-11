package com.smarthospital.security.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 password hashing.
 *
 * <p>Encoded format: {@code pbkdf2-sha256$iterations$saltBase64$hashBase64}. The
 * iteration count is stored with the hash so it can be raised without breaking
 * existing credentials. Verification is constant-time.</p>
 */
public final class PasswordHasher {
    public static final int ITERATIONS = 210_000;
    public static final String SCHEME = "PBKDF2-SHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String hash(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2-sha256$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verify(String password, String encoded) {
        if (password == null || encoded == null) return false;
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 10_000 || iterations > 5_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** A hash of a random value used to equalise timing for unknown accounts. */
    public static String dummyHash() {
        return hash(Base64.getEncoder().encodeToString(new byte[24]));
    }

    public static boolean isStrong(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) return false;
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        return upper && lower && digit && symbol;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("PBKDF2 is not available in this JVM.", ex);
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }
}
