package com.smarthospital.security.mfa.dto;

import java.time.Instant;
import java.util.List;

/** Returned once after enrolment confirmation. The plaintext recovery codes are never shown again. */
public class MfaEnrollmentConfirmResponse {
    public List<String> recoveryCodes;
    public Instant activatedAt;
}
