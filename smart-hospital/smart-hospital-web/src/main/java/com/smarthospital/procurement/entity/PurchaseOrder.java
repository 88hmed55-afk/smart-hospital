package com.smarthospital.procurement.entity;

import com.smarthospital.organization.entity.HospitalBranch;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, updatable = false)
    private HospitalBranch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false, updatable = false)
    private Supplier supplier;
    @Column(name = "order_number", nullable = false, length = 40, updatable = false)
    private String orderNumber;
    @Column(name = "order_status", nullable = false, length = 24)
    private String status;
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;
    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "ordered_at")
    private Instant orderedAt;
    @Column(name = "expected_delivery_on")
    private LocalDate expectedDeliveryOn;
    @Column(name = "notes", length = 1000)
    private String notes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() { }

    public static PurchaseOrder draft(HospitalBranch branch, Supplier supplier, String orderNumber,
                                      UUID requestedBy, LocalDate expectedDeliveryOn, String notes) {
        PurchaseOrder order = new PurchaseOrder();
        order.id = UUID.randomUUID();
        order.branch = branch;
        order.supplier = supplier;
        order.orderNumber = orderNumber;
        order.status = "DRAFT";
        order.currencyCode = branch.getDefaultCurrency();
        order.subtotal = BigDecimal.ZERO.setScale(2);
        order.discountAmount = BigDecimal.ZERO.setScale(2);
        order.taxAmount = BigDecimal.ZERO.setScale(2);
        order.totalAmount = BigDecimal.ZERO.setScale(2);
        order.requestedBy = requestedBy;
        order.expectedDeliveryOn = expectedDeliveryOn;
        order.notes = notes;
        return order;
    }

    public void addItem(PurchaseOrderItem item) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only draft orders accept lines.");
        items.add(item);
    }

    public void recalculate() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        for (PurchaseOrderItem item : items) {
            sub = sub.add(item.getGrossTotal());
            discount = discount.add(item.getLineDiscount());
        }
        subtotal = sub.setScale(2);
        discountAmount = discount.setScale(2);
        taxAmount = BigDecimal.ZERO.setScale(2);
        totalAmount = subtotal.subtract(discountAmount).add(taxAmount).setScale(2);
    }

    public void submit() { requireStatus("DRAFT"); status = "SUBMITTED"; }
    public void approve(UUID actor) { requireStatus("SUBMITTED"); status = "APPROVED"; approvedBy = actor; }
    public void reject(UUID actor) { requireStatus("SUBMITTED"); status = "REJECTED"; approvedBy = actor; }
    public void markOrdered() { requireStatus("APPROVED"); status = "ORDERED"; orderedAt = Instant.now(); }
    public void markPartiallyReceived() { if (!"ORDERED".equals(status) && !"PARTIALLY_RECEIVED".equals(status)) throw new IllegalStateException("Order is not awaiting receipt."); status = "PARTIALLY_RECEIVED"; }
    public void markReceived() { if (!"ORDERED".equals(status) && !"PARTIALLY_RECEIVED".equals(status)) throw new IllegalStateException("Order is not awaiting receipt."); status = "RECEIVED"; }
    public void cancel() {
        if (!List.of("DRAFT", "SUBMITTED", "APPROVED").contains(status)) throw new IllegalStateException("This purchase order can no longer be cancelled.");
        status = "CANCELLED";
    }
    private void requireStatus(String expected) { if (!expected.equals(status)) throw new IllegalStateException("Expected status " + expected + " but found " + status); }

    @PrePersist
    void prePersist() { if (id == null) id = UUID.randomUUID(); Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public HospitalBranch getBranch() { return branch; }
    public UUID getBranchId() { return branch.getId(); }
    public Supplier getSupplier() { return supplier; }
    public String getOrderNumber() { return orderNumber; }
    public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getOrderedAt() { return orderedAt; }
    public LocalDate getExpectedDeliveryOn() { return expectedDeliveryOn; }
    public String getNotes() { return notes; }
    public List<PurchaseOrderItem> getItems() { return Collections.unmodifiableList(items); }
}
