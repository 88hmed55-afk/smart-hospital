package com.smarthospital.procurement.dto;

import com.smarthospital.procurement.entity.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PurchaseOrderResponse {
    public UUID id; public UUID branchId; public UUID supplierId; public String supplierName; public String orderNumber;
    public String status; public String currencyCode; public BigDecimal subtotal; public BigDecimal discountAmount;
    public BigDecimal taxAmount; public BigDecimal totalAmount; public UUID requestedBy; public UUID approvedBy;
    public Instant orderedAt; public LocalDate expectedDeliveryOn; public String notes; public List<PurchaseOrderItemResponse> items;
    public static PurchaseOrderResponse from(PurchaseOrder order) {
        PurchaseOrderResponse x = new PurchaseOrderResponse(); x.id=order.getId(); x.branchId=order.getBranchId();
        x.supplierId=order.getSupplier().getId(); x.supplierName=order.getSupplier().getSupplierName(); x.orderNumber=order.getOrderNumber();
        x.status=order.getStatus(); x.currencyCode=order.getCurrencyCode(); x.subtotal=order.getSubtotal(); x.discountAmount=order.getDiscountAmount();
        x.taxAmount=order.getTaxAmount(); x.totalAmount=order.getTotalAmount(); x.requestedBy=order.getRequestedBy(); x.approvedBy=order.getApprovedBy();
        x.orderedAt=order.getOrderedAt(); x.expectedDeliveryOn=order.getExpectedDeliveryOn(); x.notes=order.getNotes();
        x.items=order.getItems().stream().map(PurchaseOrderItemResponse::from).toList(); return x;
    }
}
