package com.smarthospital.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class AccountStatusRequest {
    @NotNull public java.util.UUID branchId;
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") public String accountStatus;
}
