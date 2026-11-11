package com.smarthospital.administration.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountAdminResponse {
    public UUID id;
    public UUID staffId;
    public String staffName;
    public String username;
    public String accountStatus;
    public boolean mustChangePassword;
    public boolean mfaEnabled;
    public Instant lastLoginAt;
    public Instant createdAt;
    public List<String> roleCodes = new ArrayList<>();
    public List<UUID> branchIds = new ArrayList<>();
    public UUID defaultBranchId;
}
