package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.DiagnosisClinicalStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class DiagnosisCreateRequest {
    @NotNull public UUID diagnosisId;
    public boolean primary;
    @NotNull public DiagnosisClinicalStatus clinicalStatus;
}
