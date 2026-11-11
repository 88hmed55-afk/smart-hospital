package com.smarthospital.patient.dto;

import java.time.Instant;
import java.util.UUID;

public class ConsentResponse {
    public UUID id;
    public UUID patientId;
    public String consentType;
    public String status;
    public Instant grantedAt;
    public Instant revokedAt;
    public Instant expiresAt;
    public String documentReference;
    public String notes;
    public Instant createdAt;
}
