package com.smarthospital.laboratory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LabSpecimenRequest {
    @NotBlank
    @Size(max = 48)
    public String specimenCode;
}
