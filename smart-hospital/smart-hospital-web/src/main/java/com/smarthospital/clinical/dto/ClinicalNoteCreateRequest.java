package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.ClinicalNoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ClinicalNoteCreateRequest {
    @NotNull public ClinicalNoteType noteType;
    @NotBlank @Size(max = 20000) public String noteText;
}
