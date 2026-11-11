package com.smarthospital.patient.entity;

import com.smarthospital.organization.entity.HospitalBranch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "patient_branch_access")
public class PatientBranchAccess {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private HospitalBranch branch;

    @Column(name = "access_reason", nullable = false, length = 160)
    private String accessReason;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PatientBranchAccess() { }

    public PatientBranchAccess(Patient patient, HospitalBranch branch, UUID grantedBy, String accessReason) {
        this.patient = patient;
        this.branch = branch;
        this.grantedBy = grantedBy;
        this.accessReason = accessReason;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (grantedAt == null) grantedAt = Instant.now();
    }
}
