package com.smarthospital.pharmacy.entity;

import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.organization.entity.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "prescriptions")
public class Prescription {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescriber_staff_id", nullable = false, updatable = false)
    private DoctorProfile prescriber;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PrescriptionStatus status;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    protected Prescription() { }

    public static Prescription create(Encounter encounter, DoctorProfile prescriber) {
        Prescription prescription = new Prescription();
        prescription.encounter = encounter;
        prescription.prescriber = prescriber;
        prescription.branchId = encounter.getBranchId();
        prescription.status = PrescriptionStatus.ACTIVE;
        return prescription;
    }

    public void updateStatus(PrescriptionStatus status) {
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (orderedAt == null) orderedAt = Instant.now();
        if (status == null) status = PrescriptionStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getEncounterId() { return encounter.getId(); }
    public Encounter getEncounter() { return encounter; }
    public UUID getBranchId() { return branchId == null ? encounter.getBranchId() : branchId; }
    public UUID getPatientId() { return encounter.getPatientId(); }
    public UUID getPrescriberStaffId() { return prescriber.getStaffId(); }
    public PrescriptionStatus getStatus() { return status; }
    public Instant getOrderedAt() { return orderedAt; }
}
