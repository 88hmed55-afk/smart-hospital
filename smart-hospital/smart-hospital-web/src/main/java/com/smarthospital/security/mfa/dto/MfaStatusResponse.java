package com.smarthospital.security.mfa.dto;

import com.smarthospital.security.mfa.MfaService;
import java.time.Instant;

/** Current MFA posture for the authenticated caller. */
public class MfaStatusResponse {
    public boolean enabled;
    public long recoveryCodesRemaining;
    public Instant enrolledAt;

    public static MfaStatusResponse from(MfaService.Status status) {
        MfaStatusResponse response = new MfaStatusResponse();
        response.enabled = status.enabled();
        response.recoveryCodesRemaining = status.recoveryCodesRemaining();
        response.enrolledAt = status.enrolledAt();
        return response;
    }
}
