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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "encounter_care_team")
public class EncounterCareTeam {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, updatable = false)
    private StaffMember staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_role", nullable = false, length = 24, updatable = false)
    private CareRole careRole;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected EncounterCareTeam() { }

    public static EncounterCareTeam assign(Encounter encounter, StaffMember staff, CareRole role, UUID assignedBy) {
        EncounterCareTeam assignment = new EncounterCareTeam();
        assignment.encounter = encounter;
        assignment.staff = staff;
        assignment.careRole = role;
        assignment.assignedBy = assignedBy;
        return assignment;
    }

    public void release() {
        if (releasedAt != null) {
            throw new IllegalStateException("Care team assignment has already been released.");
        }
        releasedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (assignedAt == null) assignedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEncounterId() { return encounter.getId(); }
    public UUID getStaffId() { return staff.getId(); }
    public String getStaffName() { return staff.getFirstName() + " " + staff.getLastName(); }
    public CareRole getCareRole() { return careRole; }
    public UUID getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getReleasedAt() { return releasedAt; }
}
