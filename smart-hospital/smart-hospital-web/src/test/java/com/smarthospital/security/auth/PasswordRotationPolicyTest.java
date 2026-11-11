package com.smarthospital.security.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordRotationPolicyTest {

    @Test
    void allowsOnlyTheRotationEscapeHatches() {
        assertTrue(AuthContextFilter.isAllowedDuringRotation("security/me"));
        assertTrue(AuthContextFilter.isAllowedDuringRotation("auth/password"));
        assertTrue(AuthContextFilter.isAllowedDuringRotation("auth/logout"));
    }

    @Test
    void normalisesLeadingAndTrailingSlashes() {
        assertTrue(AuthContextFilter.isAllowedDuringRotation("/security/me/"));
        assertTrue(AuthContextFilter.isAllowedDuringRotation("//auth/password//"));
    }

    @Test
    void blocksClinicalAndAdministrativePaths() {
        assertFalse(AuthContextFilter.isAllowedDuringRotation("patients"));
        assertFalse(AuthContextFilter.isAllowedDuringRotation("patients/08ad/allergies"));
        assertFalse(AuthContextFilter.isAllowedDuringRotation("admin/roles"));
        assertFalse(AuthContextFilter.isAllowedDuringRotation("security/metrics"));
        assertFalse(AuthContextFilter.isAllowedDuringRotation("audit-logs/integrity"));
    }

    @Test
    void blocksMissingOrEmptyPaths() {
        assertFalse(AuthContextFilter.isAllowedDuringRotation(null));
        assertFalse(AuthContextFilter.isAllowedDuringRotation(""));
        assertFalse(AuthContextFilter.isAllowedDuringRotation("/"));
    }
}
