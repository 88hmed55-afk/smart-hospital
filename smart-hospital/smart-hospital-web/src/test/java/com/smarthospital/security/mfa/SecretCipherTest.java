package com.smarthospital.security.mfa;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretCipherTest {

    @Test
    void encryptsAndDecryptsWithTheSameKey() {
        byte[] key = SecretCipher.generateKey();
        String secret = TotpService.generateSecret();
        String envelope = SecretCipher.encrypt(secret, key);
        assertEquals(secret, SecretCipher.decrypt(envelope, key));
    }

    @Test
    void usesAFreshIvPerEncryption() {
        byte[] key = SecretCipher.generateKey();
        String secret = TotpService.generateSecret();
        assertNotEquals(SecretCipher.encrypt(secret, key), SecretCipher.encrypt(secret, key));
    }

    @Test
    void rejectsTamperedEnvelope() {
        byte[] key = SecretCipher.generateKey();
        String envelope = SecretCipher.encrypt(TotpService.generateSecret(), key);
        int index = envelope.length() / 2;
        char replacement = envelope.charAt(index) == 'A' ? 'B' : 'A';
        String tampered = envelope.substring(0, index) + replacement + envelope.substring(index + 1);
        assertThrows(IllegalStateException.class, () -> SecretCipher.decrypt(tampered, key));
    }

    @Test
    void rejectsWrongKey() {
        String envelope = SecretCipher.encrypt(TotpService.generateSecret(), SecretCipher.generateKey());
        assertThrows(IllegalStateException.class,
                () -> SecretCipher.decrypt(envelope, SecretCipher.generateKey()));
    }

    @Test
    void encodesKeyAsBase64() {
        byte[] key = SecretCipher.generateKey();
        assertEquals(key.length, Base64.getDecoder().decode(SecretCipher.encodeKey(key)).length);
        assertThrows(IllegalArgumentException.class,
                () -> SecretCipher.encrypt("value", new byte[16]));
    }
}
