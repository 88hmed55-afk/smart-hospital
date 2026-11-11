package com.smarthospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Records a patient allergy or intolerance in the clinical chart. */
public class AllergyCreateRequest {
    @NotBlank
    @Size(max = 160)
    public String allergenName;

    @Size(max = 500)
    public String reaction;

    /** MILD, MODERATE, SEVERE, LIFE_THREATENING or UNKNOWN. */
    @Size(max = 16)
    public String severity;

    /** UNVERIFIED, VERIFIED or REFUTED. */
    @Size(max = 20)
    public String verificationStatus;
}
