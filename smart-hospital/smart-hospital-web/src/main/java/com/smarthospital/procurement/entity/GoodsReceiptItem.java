package com.smarthospital.procurement.entity;

import com.smarthospital.pharmacy.entity.Medication;
import com.smarthospital.pharmacy.entity.MedicationBatch;
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
@Table(schema = "hospital", name = "goods_receipt_items")
public class GoodsReceiptItem {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id", nullable = false, updatable = false) private GoodsReceipt receipt;
    @Column(name = "purchase_order_id", nullable = false, updatable = false) private UUID purchaseOrderId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_item_id", nullable = false, updatable = false) private PurchaseOrderItem purchaseOrderItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "medication_id", nullable = false, updatable = false) private Medication medication;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "medication_batch_id", updatable = false) private MedicationBatch batch;
    @Column(name = "accepted_quantity", nullable = false, precision = 14, scale = 3, updatable = false) private BigDecimal acceptedQuantity;
    @Column(name = "rejected_quantity", nullable = false, precision = 14, scale = 3, updatable = false) private BigDecimal rejectedQuantity;
    @Column(name = "rejection_reason", length = 300, updatable = false) private String rejectionReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected GoodsReceiptItem() { }
    public static GoodsReceiptItem create(GoodsReceipt receipt, PurchaseOrderItem orderItem,
                                          BigDecimal accepted, BigDecimal rejected,
                                          MedicationBatch batch, String reason) {
        GoodsReceiptItem item = new GoodsReceiptItem(); item.id = UUID.randomUUID();
        item.receipt = receipt; item.purchaseOrderId = receipt.getPurchaseOrder().getId(); item.purchaseOrderItem = orderItem; item.medication = orderItem.getMedication();
        item.acceptedQuantity = accepted; item.rejectedQuantity = rejected; item.batch = batch; item.rejectionReason = reason;
        return item;
    }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); }
}
