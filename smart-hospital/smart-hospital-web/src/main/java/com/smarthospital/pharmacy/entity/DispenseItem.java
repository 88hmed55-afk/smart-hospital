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
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "dispense_items")
public class DispenseItem {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispense_id", nullable = false, updatable = false)
    private Dispense dispense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_item_id", nullable = false, updatable = false)
    private PrescriptionItem prescriptionItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private MedicationBatch batch;

    @Column(name = "quantity_dispensed", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityDispensed;

    protected DispenseItem() { }

    public static DispenseItem create(Dispense dispense, PrescriptionItem prescriptionItem,
                                      MedicationBatch batch, BigDecimal quantity) {
        DispenseItem item = new DispenseItem();
        item.dispense = dispense;
        item.prescriptionItem = prescriptionItem;
        item.batch = batch;
        item.quantityDispensed = quantity;
        return item;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public Dispense getDispense() { return dispense; }
    public PrescriptionItem getPrescriptionItem() { return prescriptionItem; }
    public MedicationBatch getBatch() { return batch; }
    public BigDecimal getQuantityDispensed() { return quantityDispensed; }
}
