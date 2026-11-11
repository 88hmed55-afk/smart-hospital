package com.smarthospital.security.mfa.dto;

import java.time.Instant;
import java.util.List;

/** Newly generated recovery codes; replaces every previous code. */
public class MfaRecoveryCodesResponse {
    public List<String> recoveryCodes;
    public Instant generatedAt;
}
