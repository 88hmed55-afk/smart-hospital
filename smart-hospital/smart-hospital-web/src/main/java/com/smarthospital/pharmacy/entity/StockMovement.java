package com.smarthospital.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "stock_movements")
public class StockMovement {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private MedicationBatch batch;

    @Column(name = "movement_type", nullable = false, length = 20, updatable = false)
    private String movementType;

    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityDelta;

    @Column(name = "reference_type", length = 40, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(name = "reason", length = 300, updatable = false)
    private String reason;

    @Column(name = "performed_by", updatable = false)
    private UUID performedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockMovement() { }

    public static StockMovement dispense(InventoryLocation location, MedicationBatch batch,
                                         BigDecimal quantity, UUID dispenseId, UUID performedBy) {
        StockMovement movement = new StockMovement();
        movement.location = location;
        movement.batch = batch;
        movement.movementType = "DISPENSE";
        movement.quantityDelta = quantity.negate();
        movement.referenceType = "DISPENSE";
        movement.referenceId = dispenseId;
        movement.reason = "Prescription dispensing";
        movement.performedBy = performedBy;
        return movement;
    }

    public static StockMovement receipt(InventoryLocation location, MedicationBatch batch,
                                        BigDecimal quantity, UUID receiptId, UUID performedBy) {
        StockMovement movement = new StockMovement();
        movement.location = location; movement.batch = batch; movement.movementType = "RECEIPT";
        movement.quantityDelta = quantity; movement.referenceType = "GOODS_RECEIPT";
        movement.referenceId = receiptId; movement.reason = "Purchase order goods receipt";
        movement.performedBy = performedBy;
        return movement;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
