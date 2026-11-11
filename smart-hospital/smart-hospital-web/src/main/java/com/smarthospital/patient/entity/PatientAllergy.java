package com.smarthospital.patient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "patient_allergies")
public class PatientAllergy {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "allergen_name", nullable = false, length = 160)
    private String allergenName;

    @Column(name = "reaction", length = 500)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AllergySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private AllergyVerificationStatus verificationStatus;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected PatientAllergy() { }

    public PatientAllergy(String allergenName, String reaction, AllergySeverity severity,
                          AllergyVerificationStatus verificationStatus) {
        this.allergenName = allergenName;
        this.reaction = reaction;
        this.severity = severity == null ? AllergySeverity.UNKNOWN : severity;
        this.verificationStatus = verificationStatus == null ? AllergyVerificationStatus.UNVERIFIED : verificationStatus;
    }

    void attachTo(Patient patient) { this.patient = patient; }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (recordedAt == null) recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAllergenName() { return allergenName; }
    public String getReaction() { return reaction; }
    public AllergySeverity getSeverity() { return severity; }
    public AllergyVerificationStatus getVerificationStatus() { return verificationStatus; }
    public Instant getRecordedAt() { return recordedAt; }
}
