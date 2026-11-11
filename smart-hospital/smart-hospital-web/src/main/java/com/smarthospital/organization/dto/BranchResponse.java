package com.smarthospital.organization.dto;

import com.smarthospital.organization.entity.HospitalBranch;
import java.util.UUID;

public class BranchResponse {
    public UUID id;
    public String code;
    public String name;
    public String timezone;
    public String defaultCurrency;
    public boolean active;

    public BranchResponse() { }

    public static BranchResponse from(HospitalBranch branch) {
        BranchResponse dto = new BranchResponse();
        dto.id = branch.getId();
        dto.code = branch.getCode();
        dto.name = branch.getName();
        dto.timezone = branch.getTimezone();
        dto.defaultCurrency = branch.getDefaultCurrency();
        dto.active = branch.isActive();
        return dto;
    }
}
