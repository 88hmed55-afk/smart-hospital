package com.smarthospital.clinical.entity;

import com.smarthospital.organization.entity.StaffMember;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "hospital",
        name = "encounter_diagnoses",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_encounter_diagnosis",
                columnNames = {"encounter_id", "diagnosis_id"}))
public class EncounterDiagnosis {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_id", nullable = false, updatable = false)
    private DiagnosisCatalog diagnosis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false, updatable = false)
    private StaffMember recordedBy;

    @Column(name = "is_primary", nullable = false, updatable = false)
    private boolean primaryDiagnosis;

    @Enumerated(EnumType.STRING)
    @Column(name = "clinical_status", nullable = false, length = 20)
    private DiagnosisClinicalStatus clinicalStatus;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected EncounterDiagnosis() {
    }

    public static EncounterDiagnosis record(
            Encounter encounter,
            DiagnosisCatalog diagnosis,
            StaffMember staff,
            boolean primary,
            DiagnosisClinicalStatus status) {
        EncounterDiagnosis link = new EncounterDiagnosis();
        link.encounter = encounter;
        link.diagnosis = diagnosis;
        link.recordedBy = staff;
        link.primaryDiagnosis = primary;
        link.clinicalStatus = status;
        return link;
    }

    public void updateClinicalStatus(DiagnosisClinicalStatus status) {
        this.clinicalStatus = status;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public Encounter getEncounter() { return encounter; }
    public DiagnosisCatalog getDiagnosis() { return diagnosis; }
    public boolean isPrimary() { return primaryDiagnosis; }
    public DiagnosisClinicalStatus getClinicalStatus() { return clinicalStatus; }
    public Instant getRecordedAt() { return recordedAt; }
}
