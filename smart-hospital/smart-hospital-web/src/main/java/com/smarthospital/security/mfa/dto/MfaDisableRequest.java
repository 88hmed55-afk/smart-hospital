package com.smarthospital.security.mfa.dto;

/** Re-authentication for sensitive MFA changes. */
public class MfaDisableRequest {
    public String password;
    public String code;
}
