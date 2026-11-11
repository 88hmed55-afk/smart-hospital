package com.smarthospital.security.auth.dto;

import com.smarthospital.security.dto.CurrentSessionResponse;
import java.time.Instant;

/**
 * Authentication result. The raw bearer token is returned only after all required
 * factors succeed. When {@code mfaRequired} is true, no token or user is returned:
 * the client must post the challenge token and code to {@code /auth/mfa/verify}.
 */
public class LoginResponse {
    public String token;
    public String tokenType = "Bearer";
    public Instant expiresAt;
    public boolean mustChangePassword;
    /** True only when the deployment enforces rotation; lets the UI decide to force the screen. */
    public boolean passwordRotationEnforced;
    /** True when the password was accepted but a TOTP/recovery code is still required. */
    public boolean mfaRequired;
    /** Short-lived token for {@code /auth/mfa/verify}; never a bearer session. */
    public String mfaChallengeToken;
    /** True when the account's role must enrol MFA before using the application. */
    public boolean mfaEnrollmentRequired;
    public CurrentSessionResponse user;
}
