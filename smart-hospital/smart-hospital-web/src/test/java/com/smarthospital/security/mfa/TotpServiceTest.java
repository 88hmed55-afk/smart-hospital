package com.smarthospital.security.mfa;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TotpServiceTest {
    /** RFC 6238 Appendix B SHA-1 test secret (ASCII "12345678901234567890"). */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238Sha1Vectors() {
        assertCode(59L, "94287082");
        assertCode(1111111109L, "07081804");
        assertCode(1111111111L, "14050471");
        assertCode(1234567890L, "89005924");
        assertCode(2000000000L, "69279037");
        assertCode(20000000000L, "65353130");
    }

    @Test
    void acceptsClockSkewAndRejectsReplay() {
        long now = 1_700_000_000L;
        long current = now / TotpService.DEFAULT_PERIOD_SECONDS;
        String currentCode = TotpService.code(RFC_SECRET, current, 6);
        OptionalLong accepted = TotpService.verify(RFC_SECRET, currentCode, null, now, 1);
        assertTrue(accepted.isPresent());
        assertEquals(current, accepted.getAsLong());
        assertTrue(TotpService.verify(RFC_SECRET, currentCode, accepted.getAsLong(), now, 1).isEmpty());
    }

    @Test
    void acceptsPreviousStepWithinWindow() {
        long now = 1_700_000_000L;
        long current = now / TotpService.DEFAULT_PERIOD_SECONDS;
        String previous = TotpService.code(RFC_SECRET, current - 1, 6);
        OptionalLong accepted = TotpService.verify(RFC_SECRET, previous, null, now, 1);
        assertTrue(accepted.isPresent());
        assertEquals(current - 1, accepted.getAsLong());
    }

    @Test
    void rejectsMalformedCodes() {
        assertTrue(TotpService.verify(RFC_SECRET, "12345", null).isEmpty());
        assertTrue(TotpService.verify(RFC_SECRET, "abcdef", null).isEmpty());
        assertTrue(TotpService.verify(RFC_SECRET, null, null).isEmpty());
        assertFalse(TotpService.normalizeCode("12 34 56 ").isEmpty());
    }

    @Test
    void base32RoundTrips() {
        String secret = TotpService.generateSecret();
        assertEquals(32, secret.length());
        assertEquals(secret, TotpService.encodeBase32(TotpService.decodeBase32(secret)));
        assertThrows(IllegalArgumentException.class, () -> TotpService.decodeBase32("not-base32!"));
    }

    @Test
    void buildsStandardOtpauthUri() {
        String uri = TotpService.otpauthUri("Smart Hospital", "doctor1", RFC_SECRET);
        assertTrue(uri.startsWith("otpauth://totp/Smart%20Hospital:doctor1?"));
        assertTrue(uri.contains("secret=" + RFC_SECRET));
        assertTrue(uri.contains("issuer=Smart%20Hospital"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }

    private static void assertCode(long epochSecond, String expected) {
        String actual = TotpService.code(RFC_SECRET,
                epochSecond / TotpService.DEFAULT_PERIOD_SECONDS, 8);
        assertEquals(expected, actual, "TOTP mismatch at epoch " + epochSecond);
    }
}
