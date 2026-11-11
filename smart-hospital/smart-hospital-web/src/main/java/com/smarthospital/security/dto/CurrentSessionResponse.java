package com.smarthospital.security.dto;

import com.smarthospital.security.domain.CurrentUser;
import java.util.List;
import java.util.UUID;

/** Safe identity projection for the authenticated user's own session. */
public class CurrentSessionResponse {
    public UUID userId;
    public UUID staffId;
    public String username;
    public List<String> roleCodes;
    public List<UUID> branchIds;
    public UUID defaultBranchId;
    public boolean mfaEnabled;
    public boolean mfaEnrollmentRequired;

    public CurrentSessionResponse() { }

    public static CurrentSessionResponse from(CurrentUser user) {
        CurrentSessionResponse response = new CurrentSessionResponse();
        response.userId = user.userId();
        response.staffId = user.staffId();
        response.username = user.username();
        response.roleCodes = user.roleCodes();
        response.branchIds = user.branchIds();
        response.defaultBranchId = user.defaultBranchId();
        response.mfaEnabled = user.mfaEnabled();
        response.mfaEnrollmentRequired = user.mfaEnrollmentRequired();
        return response;
    }
}
