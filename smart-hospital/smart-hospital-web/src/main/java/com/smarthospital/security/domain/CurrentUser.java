package com.smarthospital.security.domain;

import java.util.List;
import java.util.UUID;

/** Authenticated hospital account resolved from a bearer session. */
public record CurrentUser(
        UUID userId,
        UUID staffId,
        String username,
        List<String> roleCodes,
        List<UUID> branchIds,
        UUID defaultBranchId,
        boolean mustChangePassword,
        boolean mfaEnabled,
        boolean mfaEnrollmentRequired
) {
    public CurrentUser {
        roleCodes = List.copyOf(roleCodes);
        branchIds = List.copyOf(branchIds);
    }
}
