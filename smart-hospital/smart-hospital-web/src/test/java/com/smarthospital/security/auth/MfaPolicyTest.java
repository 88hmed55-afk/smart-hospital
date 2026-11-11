package com.smarthospital.security.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaPolicyTest {
    private String originalRequiredRoles;

    @AfterEach
    void restore() {
        if (originalRequiredRoles == null) {
            System.clearProperty("smarthospital.mfa.requiredRoles");
        } else {
            System.setProperty("smarthospital.mfa.requiredRoles", originalRequiredRoles);
        }
    }

    @Test
    void allowsOnlyEnrolmentPathsDuringForcedEnrolment() {
        assertTrue(AuthContextFilter.isAllowedDuringMfaEnrollment("security/me"));
        assertTrue(AuthContextFilter.isAllowedDuringMfaEnrollment("/auth/mfa/enroll/start/"));
        assertTrue(AuthContextFilter.isAllowedDuringMfaEnrollment("auth/mfa/enroll/confirm"));
        assertTrue(AuthContextFilter.isAllowedDuringMfaEnrollment("auth/logout"));
        assertFalse(AuthContextFilter.isAllowedDuringMfaEnrollment("patients"));
        assertFalse(AuthContextFilter.isAllowedDuringMfaEnrollment("auth/mfa/disable"));
        assertFalse(AuthContextFilter.isAllowedDuringMfaEnrollment(null));
    }

    @Test
    void requiredRolesMatchCaseInsensitively() {
        originalRequiredRoles = System.getProperty("smarthospital.mfa.requiredRoles");
        System.setProperty("smarthospital.mfa.requiredRoles", "hospital_admin, Doctor");
        assertTrue(SecuritySettings.mfaRequiredFor(List.of("DOCTOR")));
        assertTrue(SecuritySettings.mfaRequiredFor(List.of("HOSPITAL_ADMIN")));
        assertFalse(SecuritySettings.mfaRequiredFor(List.of("NURSE")));
        assertTrue(SecuritySettings.mfaEnrollmentEnforced());
    }

    @Test
    void emptyConfigurationKeepsEnrolmentOptional() {
        originalRequiredRoles = System.getProperty("smarthospital.mfa.requiredRoles");
        System.setProperty("smarthospital.mfa.requiredRoles", "");
        assertFalse(SecuritySettings.mfaRequiredFor(List.of("HOSPITAL_ADMIN")));
        assertFalse(SecuritySettings.mfaEnrollmentEnforced());
    }
}
