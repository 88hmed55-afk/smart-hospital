package com.smarthospital.security.mfa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MfaRecoveryCodeTest {

    @Test
    void normalisesCaseSeparatorsAndWhitespace() {
        assertEquals("ABCDEFGHIJ", MfaService.normalizeRecoveryCode("abcde-fghij"));
        assertEquals("ABCDEFGHIJ", MfaService.normalizeRecoveryCode(" ABCDE FGHIJ "));
        assertEquals("ABCD234567", MfaService.normalizeRecoveryCode("abcd-234-567"));
    }

    @Test
    void rejectsWrongLengthOrCharacters() {
        assertNull(MfaService.normalizeRecoveryCode(null));
        assertNull(MfaService.normalizeRecoveryCode("ABCD"));
        assertNull(MfaService.normalizeRecoveryCode("ABCD-EFGHI"));
    }
}
