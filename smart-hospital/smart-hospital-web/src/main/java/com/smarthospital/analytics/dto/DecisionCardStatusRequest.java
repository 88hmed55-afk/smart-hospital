package com.smarthospital.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DecisionCardStatusRequest {
    @NotBlank
    @Pattern(regexp = "ACKNOWLEDGED|IN_PROGRESS|COMPLETED|DISMISSED")
    public String status;

    public DecisionCardStatusRequest() { }
}
