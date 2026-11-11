package com.smarthospital.security.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    @Test
    void issuesUrlSafeTokensWithPrefix() {
        String token = TokenService.newToken();
        assertTrue(token.startsWith("sh_"));
        assertTrue(token.length() > 40);
        assertTrue(token.matches("^sh_[A-Za-z0-9_-]+$"));
    }

    @Test
    void issuesUniqueTokens() {
        assertNotEquals(TokenService.newToken(), TokenService.newToken());
    }

    @Test
    void hashesDeterministicallyToSixtyFourHexCharacters() {
        String token = TokenService.newToken();
        String hash = TokenService.hash(token);
        assertEquals(64, hash.length());
        assertEquals(hash, TokenService.hash(token));
        assertTrue(hash.matches("^[0-9a-f]{64}$"));
    }

    @Test
    void matchesOnlyTheRawTokenThatProducedTheHash() {
        String token = TokenService.newToken();
        String hash = TokenService.hash(token);
        assertTrue(TokenService.matches(token, hash));
        assertFalse(TokenService.matches(token + "x", hash));
        assertFalse(TokenService.matches(null, hash));
        assertFalse(TokenService.matches(token, null));
    }

    @Test
    void hashesKnownVector() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TokenService.sha256Hex("abc"));
    }
}
