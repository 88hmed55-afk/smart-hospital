package com.smarthospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Records a consent decision (for example treatment, data sharing or research). */
public class ConsentCreateRequest {
    @NotBlank
    @Size(max = 60)
    public String consentType;

    /** GRANTED, REVOKED or EXPIRED. Defaults to GRANTED. */
    @Size(max = 20)
    public String status;

    public Instant expiresAt;

    @Size(max = 200)
    public String documentReference;

    @Size(max = 600)
    public String notes;
}
