package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.CareRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CareTeamAssignRequest {
    @NotNull
    public UUID staffId;

    @NotNull
    public CareRole careRole;
}
