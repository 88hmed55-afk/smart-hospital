package com.smarthospital.admission.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(schema="hospital", name="bed_assignments")
public class BedAssignment {
    @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
    @Column(name="branch_id", nullable=false, updatable=false) private UUID branchId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="admission_id", nullable=false, updatable=false) private Admission admission;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="bed_id", nullable=false, updatable=false) private Bed bed;
    @Enumerated(EnumType.STRING) @Column(name="assignment_status", nullable=false, length=16) private BedAssignmentStatus status;
    @Column(name="started_at", nullable=false, updatable=false) private Instant startedAt;
    @Column(name="ended_at") private Instant endedAt;
    @Column(name="assigned_by", nullable=false, updatable=false) private UUID assignedBy;
    @Column(name="released_by") private UUID releasedBy;
    @Column(name="transfer_reason", length=500) private String transferReason;
    protected BedAssignment() {}
    public static BedAssignment assign(Admission admission, Bed bed, UUID userId, String reason, Instant at){BedAssignment x=new BedAssignment();x.id=UUID.randomUUID();x.branchId=admission.getBranchId();x.admission=admission;x.bed=bed;x.status=BedAssignmentStatus.ACTIVE;x.assignedBy=userId;x.transferReason=reason;x.startedAt=at;return x;}
    public void release(UUID userId, Instant at, boolean cancelled){if(status!=BedAssignmentStatus.ACTIVE)throw new IllegalStateException("Only active bed assignment can be released.");if(!at.isAfter(startedAt))at=startedAt.plusNanos(1);status=cancelled?BedAssignmentStatus.CANCELLED:BedAssignmentStatus.COMPLETED;endedAt=at;releasedBy=userId;}
    public UUID getId(){return id;} public UUID getAdmissionId(){return admission.getId();} public UUID getBedId(){return bed.getId();} public Bed getBed(){return bed;} public Instant getStartedAt(){return startedAt;} public Instant getEndedAt(){return endedAt;} public BedAssignmentStatus getStatus(){return status;}
}
