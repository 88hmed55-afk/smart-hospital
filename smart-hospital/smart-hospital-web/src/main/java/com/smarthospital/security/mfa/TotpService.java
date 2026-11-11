package com.smarthospital.security.mfa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP / RFC 4226 HOTP primitives.
 *
 * <p>The production authenticator uses the conventional profile: HMAC-SHA1,
 * 6 digits and a 30-second period. Verification accepts the previous, current
 * and next step (clock skew), but never a step that was already used, which
 * blocks replay of a captured code. Secrets are 160-bit Base32 strings.</p>
 */
public final class TotpService {
    public static final int DEFAULT_DIGITS = 6;
    public static final int DEFAULT_PERIOD_SECONDS = 30;
    public static final int DEFAULT_WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpService() { }

    /** Generates a fresh 160-bit Base32 secret without padding. */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /** Current time step for the production 30-second period. */
    public static long currentStep() {
        return Instant.now().getEpochSecond() / DEFAULT_PERIOD_SECONDS;
    }

    public static String currentCode(String secret) {
        return code(secret, currentStep(), DEFAULT_DIGITS);
    }

    public static String code(String secret, long step, int digits) {
        if (digits < 6 || digits > 8) throw new IllegalArgumentException("digits must be 6..8");
        byte[] key = decodeBase32(secret);
        byte[] message = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int modulus = (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulus);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA1 is not available in this JVM.", ex);
        }
    }

    /**
     * Verifies a submitted code against the current step plus/minus one step.
     *
     * @param lastAcceptedStep last step already consumed for this account, or {@code null}
     * @return the accepted step, empty when the code is invalid or replayed
     */
    public static OptionalLong verify(String secret, String code, Long lastAcceptedStep) {
        return verify(secret, code, lastAcceptedStep, Instant.now().getEpochSecond(), DEFAULT_WINDOW);
    }

    public static OptionalLong verify(String secret, String code, Long lastAcceptedStep,
                                      long epochSecond, int window) {
        String normalized = normalizeCode(code);
        if (normalized == null || secret == null || secret.isBlank()) return OptionalLong.empty();
        long current = epochSecond / DEFAULT_PERIOD_SECONDS;
        long minimum = lastAcceptedStep == null ? Long.MIN_VALUE : lastAcceptedStep;
        long found = Long.MIN_VALUE;
        for (long step = current - Math.max(0, window); step <= current + Math.max(0, window); step++) {
            if (step <= minimum) continue;
            String candidate = code(secret, step, normalized.length());
            if (MessageDigest.isEqual(candidate.getBytes(StandardCharsets.US_ASCII),
                    normalized.getBytes(StandardCharsets.US_ASCII)) && step > found) {
                found = step;
            }
        }
        return found == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(found);
    }

    /** Returns a six-digit code with spaces and dashes removed, or null for malformed input. */
    public static String normalizeCode(String code) {
        if (code == null) return null;
        String compact = code.replace(" ", "").replace("-", "").trim();
        if (compact.length() != DEFAULT_DIGITS) return null;
        for (int i = 0; i < compact.length(); i++) {
            if (!Character.isDigit(compact.charAt(i))) return null;
        }
        return compact;
    }

    /** Builds the standard otpauth URI an authenticator app can import. */
    public static String otpauthUri(String issuer, String account, String secret) {
        String safeIssuer = issuer == null || issuer.isBlank() ? "Smart Hospital" : issuer;
        String label = urlEncode(safeIssuer) + ":" + urlEncode(account == null ? "" : account);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(safeIssuer)
                + "&algorithm=SHA1&digits=" + DEFAULT_DIGITS
                + "&period=" + DEFAULT_PERIOD_SECONDS;
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        return out.toString();
    }

    public static byte[] decodeBase32(String value) {
        if (value == null) throw new IllegalArgumentException("secret is required");
        String compact = value.replace(" ", "").replace("-", "").replace("=", "")
                .toUpperCase(Locale.ROOT);
        if (compact.isEmpty()) throw new IllegalArgumentException("secret is required");
        int buffer = 0;
        int bits = 0;
        byte[] out = new byte[compact.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < compact.length(); i++) {
            int decoded = BASE32.indexOf(compact.charAt(i));
            if (decoded < 0) throw new IllegalArgumentException("invalid Base32 secret");
            buffer = (buffer << 5) | decoded;
            bits += 5;
            if (bits >= 8) {
                if (index < out.length) out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
