package com.smarthospital.security.mfa.dto;

import java.time.Instant;

/** Everything an authenticator app needs to enrol, returned once at enrolment start. */
public class MfaEnrollmentStartResponse {
    /** Base32 secret for manual entry. Shown only during enrolment. */
    public String secret;
    /** otpauth:// URI that a QR-code renderer can encode. */
    public String otpauthUri;
    public String issuer;
    public String account;
    public int digits = 6;
    public int period = 30;
    public String algorithm = "SHA1";
    public Instant pendingSince;
}
