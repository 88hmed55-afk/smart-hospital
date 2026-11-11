package com.smarthospital.security.mfa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope encryption for TOTP secrets stored in {@code user_accounts.mfa_secret}.
 *
 * <p>Values are {@code v1:} followed by Base64(IV || ciphertext || GCM tag). The
 * 256-bit key is read from {@code -Dsmarthospital.mfa.key} / {@code SMART_HOSPITAL_MFA_KEY}
 * (Base64). When no key is supplied, a key file is created once under
 * {@code ~/.smart-hospital/mfa.key} so local deployments still work; production
 * must provision the key explicitly and back it up with the encrypted database.
 * Losing the key makes enrolled TOTP secrets unrecoverable (recovery codes and
 * administrator reset remain the escape hatches).</p>
 */
public final class SecretCipher {
    private static final String PREFIX = "v1:";
    private static final String AAD = "smart-hospital-mfa-secret";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object KEY_LOCK = new Object();
    private static volatile byte[] cachedKey;

    private SecretCipher() { }

    public static String encrypt(String plaintext) {
        return encrypt(plaintext, activeKey());
    }

    public static String encrypt(String plaintext, byte[] key) {
        if (plaintext == null) throw new IllegalArgumentException("plaintext is required");
        requireKey(key);
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt the MFA secret.", ex);
        }
    }

    public static String decrypt(String payload) {
        return decrypt(payload, activeKey());
    }

    public static String decrypt(String payload, byte[] key) {
        if (payload == null || !payload.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported MFA secret envelope.");
        }
        requireKey(key);
        try {
            byte[] raw = Base64.getDecoder().decode(payload.substring(PREFIX.length()));
            if (raw.length <= IV_BYTES) throw new IllegalStateException("Truncated MFA secret envelope.");
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt the MFA secret; the key is wrong or the value was tampered with.", ex);
        }
    }

    public static byte[] generateKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    public static String encodeKey(byte[] key) {
        requireKey(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /** Resolves the configured key, creating the local key file on first use if needed. */
    public static byte[] activeKey() {
        byte[] key = cachedKey;
        if (key != null) return key;
        synchronized (KEY_LOCK) {
            if (cachedKey != null) return cachedKey;
            cachedKey = resolveKey();
            return cachedKey;
        }
    }

    private static byte[] resolveKey() {
        String configured = propertyOrEnvironment("smarthospital.mfa.key", "SMART_HOSPITAL_MFA_KEY");
        if (configured != null && !configured.isBlank()) {
            return decodeKey(configured.trim());
        }
        Path keyFile = keyFilePath();
        try {
            if (Files.exists(keyFile)) {
                return decodeKey(Files.readString(keyFile, StandardCharsets.US_ASCII).trim());
            }
            Files.createDirectories(keyFile.getParent());
            byte[] generated = generateKey();
            try {
                Files.writeString(keyFile, encodeKey(generated), StandardCharsets.US_ASCII,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                restrictPermissions(keyFile);
                return generated;
            } catch (java.nio.file.FileAlreadyExistsException ex) {
                return decodeKey(Files.readString(keyFile, StandardCharsets.US_ASCII).trim());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read or create the MFA key file: " + keyFile, ex);
        }
    }

    private static Path keyFilePath() {
        String configured = propertyOrEnvironment("smarthospital.mfa.keyFile", "SMART_HOSPITAL_MFA_KEY_FILE");
        if (configured != null && !configured.isBlank()) return Path.of(configured.trim());
        return Path.of(System.getProperty("user.home", "."), ".smart-hospital", "mfa.key");
    }

    private static byte[] decodeKey(String encoded) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            requireKey(key);
            return key;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("smarthospital.mfa.key must be a Base64-encoded 32-byte key.", ex);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs are managed by the deployment account; the file is outside the web root.
        }
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("A 256-bit AES key is required.");
        }
    }

    private static String propertyOrEnvironment(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value;
    }
}
