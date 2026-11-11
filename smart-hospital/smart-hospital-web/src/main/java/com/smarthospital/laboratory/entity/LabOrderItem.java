package com.smarthospital.laboratory.entity;

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
import jakarta.persistence.OneToOne;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "lab_order_items")
public class LabOrderItem {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false, updatable = false)
    private LabOrder labOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_id", nullable = false, updatable = false)
    private LabTestCatalog labTest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LabItemStatus status;

    @Column(name = "specimen_code", length = 48)
    private String specimenCode;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by")
    private StaffMember collectedBy;

    @OneToOne(mappedBy = "labOrderItem", fetch = FetchType.LAZY)
    private LabResult labResult;

    protected LabOrderItem() { }

    public static LabOrderItem pending(LabOrder order, LabTestCatalog test) {
        LabOrderItem item = new LabOrderItem();
        item.labOrder = order;
        item.labTest = test;
        item.status = LabItemStatus.PENDING;
        return item;
    }

    public void collect(String specimenCode, StaffMember staff) {
        if (status != LabItemStatus.PENDING) {
            throw new IllegalStateException("Only a pending laboratory item can be collected.");
        }
        this.specimenCode = specimenCode;
        this.collectedBy = staff;
        this.collectedAt = Instant.now();
        this.status = LabItemStatus.COLLECTED;
    }

    public void startProcessing() {
        if (status != LabItemStatus.COLLECTED) {
            throw new IllegalStateException("Only a collected specimen can enter processing.");
        }
        status = LabItemStatus.PROCESSING;
    }

    public void markResulted() {
        if (status != LabItemStatus.COLLECTED && status != LabItemStatus.PROCESSING) {
            throw new IllegalStateException("A result can only be entered for a collected specimen.");
        }
        status = LabItemStatus.RESULTED;
    }

    public void markReviewed() {
        if (status != LabItemStatus.RESULTED) {
            throw new IllegalStateException("Only a resulted item can be reviewed.");
        }
        status = LabItemStatus.REVIEWED;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = LabItemStatus.PENDING;
    }

    public UUID getId() { return id; }
    public LabOrder getLabOrder() { return labOrder; }
    public UUID getLabOrderId() { return labOrder.getId(); }
    public LabTestCatalog getLabTest() { return labTest; }
    public UUID getLabTestId() { return labTest.getId(); }
    public String getTestName() { return labTest.getName(); }
    public String getTestCode() { return labTest.getCode(); }
    public LabItemStatus getStatus() { return status; }
    public String getSpecimenCode() { return specimenCode; }
    public Instant getCollectedAt() { return collectedAt; }
    public UUID getCollectedBy() { return collectedBy == null ? null : collectedBy.getId(); }
    public LabResult getLabResult() { return labResult; }
}
