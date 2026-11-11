package com.smarthospital.administration.dto;

import java.util.UUID;

/**
 * Security-administrator reset of a locked-out account's second factor.
 *
 * <p>Used only after identity verification out-of-band (for example a video call
 * with the employee and their manager). The reason is mandatory and is written to
 * the audit trail.</p>
 */
public class MfaResetRequest {
    public UUID branchId;
    public String reason;
}
