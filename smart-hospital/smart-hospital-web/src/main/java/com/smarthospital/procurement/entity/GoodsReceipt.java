package com.smarthospital.procurement.entity;

import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.pharmacy.entity.InventoryLocation;
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
@Table(schema = "hospital", name = "goods_receipts")
public class GoodsReceipt {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "branch_id", nullable = false, updatable = false) private HospitalBranch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false) private PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_location_id", nullable = false, updatable = false) private InventoryLocation location;
    @Column(name = "receipt_number", nullable = false, length = 40, updatable = false) private String receiptNumber;
    @Column(name = "supplier_delivery_reference", length = 100, updatable = false) private String supplierDeliveryReference;
    @Column(name = "receipt_status", nullable = false, length = 16) private String status;
    @Column(name = "received_by", nullable = false, updatable = false) private UUID receivedBy;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "received_at", nullable = false, updatable = false) private Instant receivedAt;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "notes", length = 1000, updatable = false) private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected GoodsReceipt() { }
    public static GoodsReceipt draft(HospitalBranch branch, PurchaseOrder order, InventoryLocation location,
                                     String number, String deliveryReference, UUID receivedBy, String notes) {
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.id = UUID.randomUUID(); receipt.branch = branch; receipt.purchaseOrder = order;
        receipt.location = location; receipt.receiptNumber = number;
        receipt.supplierDeliveryReference = deliveryReference; receipt.status = "DRAFT";
        receipt.receivedBy = receivedBy; receipt.notes = notes;
        return receipt;
    }
    public void post(UUID actor) { if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft receipt can be posted."); status = "POSTED"; postedBy = actor; postedAt = Instant.now(); }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (receivedAt == null) receivedAt = Instant.now(); if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getBranchId() { return branch.getId(); }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public InventoryLocation getLocation() { return location; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getPostedAt() { return postedAt; }
}
