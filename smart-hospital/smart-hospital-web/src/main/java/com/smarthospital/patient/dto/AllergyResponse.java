package com.smarthospital.patient.dto;

import java.time.Instant;
import java.util.UUID;

public class AllergyResponse {
    public UUID id;
    public UUID patientId;
    public String allergenName;
    public String reaction;
    public String severity;
    public String verificationStatus;
    public Instant recordedAt;
}
