package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.DiagnosisClinicalStatus;
import jakarta.validation.constraints.NotNull;

public class DiagnosisStatusUpdateRequest {
    @NotNull public DiagnosisClinicalStatus clinicalStatus;
}
