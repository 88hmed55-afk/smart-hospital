package com.smarthospital.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class SystemSettingUpdateRequest {
    @NotBlank
    @Size(max = 100)
    public String key;

    /** Null means a global setting and is restricted to HOSPITAL_ADMIN. */
    public UUID branchId;

    @NotNull
    public BigDecimal value;

    public SystemSettingUpdateRequest() { }
}
