package com.smarthospital.security.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashesAndVerifiesTheSamePassword() {
        String encoded = PasswordHasher.hash("Correct-Horse-9!");
        assertTrue(encoded.startsWith("pbkdf2-sha256$" + PasswordHasher.ITERATIONS + "$"));
        assertTrue(PasswordHasher.verify("Correct-Horse-9!", encoded));
    }

    @Test
    void rejectsAWrongPassword() {
        String encoded = PasswordHasher.hash("Correct-Horse-9!");
        assertFalse(PasswordHasher.verify("correct-horse-9!", encoded));
        assertFalse(PasswordHasher.verify("", encoded));
        assertFalse(PasswordHasher.verify(null, encoded));
    }

    @Test
    void usesAFreshSaltForEveryHash() {
        assertNotEquals(PasswordHasher.hash("Same-Password-1!"), PasswordHasher.hash("Same-Password-1!"));
    }

    @Test
    void rejectsMalformedOrWeakEncodings() {
        assertFalse(PasswordHasher.verify("anything", null));
        assertFalse(PasswordHasher.verify("anything", "plaintext"));
        assertFalse(PasswordHasher.verify("anything", "pbkdf2-sha256$1$AAAA$AAAA"));
        assertFalse(PasswordHasher.verify("anything", "pbkdf2-sha256$210000$not-base64$also-bad"));
    }

    @Test
    void refusesBlankPasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("   "));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }

    @Test
    void enforcesThePasswordPolicy() {
        assertTrue(PasswordHasher.isStrong("Long-Enough-1!"));
        assertFalse(PasswordHasher.isStrong("short-1!"), "under 12 characters");
        assertFalse(PasswordHasher.isStrong("alllowercase1!"), "no uppercase");
        assertFalse(PasswordHasher.isStrong("ALLUPPERCASE1!"), "no lowercase");
        assertFalse(PasswordHasher.isStrong("NoDigitsHere!"), "no digit");
        assertFalse(PasswordHasher.isStrong("NoSymbolsHere1"), "no symbol");
    }

    @Test
    void dummyHashesAreUniqueAndVerifiableOnlyByTheirOwnSecret() {
        String first = PasswordHasher.dummyHash();
        String second = PasswordHasher.dummyHash();
        assertNotEquals(first, second);
        assertFalse(PasswordHasher.verify("guess", first));
    }
}
