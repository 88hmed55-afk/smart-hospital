package com.smarthospital.procurement.entity;

import com.smarthospital.pharmacy.entity.Medication;
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
@Table(schema = "hospital", name = "purchase_order_items")
public class PurchaseOrderItem {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false) private PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "medication_id", nullable = false, updatable = false) private Medication medication;
    @Column(name = "line_number", nullable = false, updatable = false) private int lineNumber;
    @Column(name = "item_description_snapshot", nullable = false, length = 220, updatable = false) private String description;
    @Column(name = "ordered_quantity", nullable = false, precision = 14, scale = 3, updatable = false) private BigDecimal orderedQuantity;
    @Column(name = "received_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal receivedQuantity;
    @Column(name = "unit_of_measure", nullable = false, length = 32, updatable = false) private String unitOfMeasure;
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal unitCost;
    @Column(name = "line_discount", nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal lineDiscount;
    @Column(name = "line_total", nullable = false, precision = 14, scale = 2, updatable = false) private BigDecimal lineTotal;

    protected PurchaseOrderItem() { }
    public static PurchaseOrderItem create(PurchaseOrder order, Medication medication, int lineNumber,
                                           BigDecimal quantity, String unit, BigDecimal unitCost,
                                           BigDecimal discount) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.id = UUID.randomUUID(); item.purchaseOrder = order; item.medication = medication;
        item.lineNumber = lineNumber; item.description = medication.getGenericName() + (medication.getStrength() == null ? "" : " " + medication.getStrength());
        item.orderedQuantity = quantity; item.receivedQuantity = BigDecimal.ZERO.setScale(3);
        item.unitOfMeasure = unit; item.unitCost = unitCost; item.lineDiscount = discount;
        item.lineTotal = quantity.multiply(unitCost).subtract(discount).setScale(2, java.math.RoundingMode.HALF_UP);
        return item;
    }
    public void recordReceived(BigDecimal quantity) { receivedQuantity = receivedQuantity.add(quantity).setScale(3); }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); }
    public UUID getId() { return id; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public Medication getMedication() { return medication; }
    public UUID getMedicationId() { return medication.getId(); }
    public int getLineNumber() { return lineNumber; }
    public String getDescription() { return description; }
    public BigDecimal getOrderedQuantity() { return orderedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getRemainingQuantity() { return orderedQuantity.subtract(receivedQuantity); }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getLineDiscount() { return lineDiscount; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public BigDecimal getGrossTotal() { return orderedQuantity.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP); }
}
