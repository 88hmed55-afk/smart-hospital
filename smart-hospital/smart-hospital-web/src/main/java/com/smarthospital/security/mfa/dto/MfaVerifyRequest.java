package com.smarthospital.security.mfa.dto;

/** Second step of login: the challenge token issued after the password plus a TOTP/recovery code. */
public class MfaVerifyRequest {
    public String challengeToken;
    public String code;
}
