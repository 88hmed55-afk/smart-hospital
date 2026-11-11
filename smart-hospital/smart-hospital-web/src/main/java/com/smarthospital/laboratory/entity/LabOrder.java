package com.smarthospital.laboratory.entity;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "lab_orders")
public class LabOrder {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordered_by", nullable = false, updatable = false)
    private DoctorProfile orderedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 12)
    private LabOrderPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LabOrderStatus status;

    @Column(name = "clinical_question", length = 500)
    private String clinicalQuestion;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    protected LabOrder() { }

    public static LabOrder create(Encounter encounter, DoctorProfile doctor, LabOrderPriority priority, String clinicalQuestion) {
        LabOrder order = new LabOrder();
        order.encounter = encounter;
        order.orderedBy = doctor;
        order.priority = priority == null ? LabOrderPriority.ROUTINE : priority;
        order.status = LabOrderStatus.ORDERED;
        order.clinicalQuestion = clinicalQuestion;
        return order;
    }

    public void updateStatus(LabOrderStatus nextStatus) {
        this.status = nextStatus;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (orderedAt == null) orderedAt = Instant.now();
        if (status == null) status = LabOrderStatus.ORDERED;
        if (priority == null) priority = LabOrderPriority.ROUTINE;
    }

    public UUID getId() { return id; }
    public UUID getEncounterId() { return encounter.getId(); }
    public Encounter getEncounter() { return encounter; }
    public UUID getOrderedBy() { return orderedBy.getStaffId(); }
    public LabOrderPriority getPriority() { return priority; }
    public LabOrderStatus getStatus() { return status; }
    public String getClinicalQuestion() { return clinicalQuestion; }
    public Instant getOrderedAt() { return orderedAt; }
}
