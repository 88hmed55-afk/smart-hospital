package com.smarthospital.pharmacy.entity;

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
@Table(schema = "hospital", name = "dispenses")
public class Dispense {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispensed_by", nullable = false, updatable = false)
    private StaffMember dispensedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_location_id", nullable = false, updatable = false)
    private InventoryLocation inventoryLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DispenseStatus status;

    @Column(name = "dispensed_at", nullable = false, updatable = false)
    private Instant dispensedAt;

    @Column(name = "idempotency_key", length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64, updatable = false)
    private String requestFingerprint;

    protected Dispense() { }

    public static Dispense draft(Prescription prescription, StaffMember pharmacist,
                                 InventoryLocation location, String idempotencyKey, String requestFingerprint) {
        Dispense dispense = new Dispense();
        dispense.prescription = prescription;
        dispense.dispensedBy = pharmacist;
        dispense.inventoryLocation = location;
        dispense.status = DispenseStatus.DRAFT;
        dispense.idempotencyKey = idempotencyKey;
        dispense.requestFingerprint = requestFingerprint;
        return dispense;
    }

    public void post() {
        if (status != DispenseStatus.DRAFT) {
            throw new IllegalStateException("Only a draft dispense can be posted.");
        }
        status = DispenseStatus.POSTED;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = DispenseStatus.DRAFT;
        if (dispensedAt == null) dispensedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Prescription getPrescription() { return prescription; }
    public UUID getPrescriptionId() { return prescription.getId(); }
    public UUID getBranchId() { return prescription.getBranchId(); }
    public StaffMember getDispensedBy() { return dispensedBy; }
    public InventoryLocation getInventoryLocation() { return inventoryLocation; }
    public DispenseStatus getStatus() { return status; }
    public Instant getDispensedAt() { return dispensedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
}
