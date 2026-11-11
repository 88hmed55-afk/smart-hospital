package com.smarthospital.procurement.dto;

import com.smarthospital.procurement.entity.GoodsReceipt;
import java.time.Instant;
import java.util.UUID;

public class GoodsReceiptResponse {
    public UUID id; public UUID branchId; public UUID purchaseOrderId; public String orderNumber; public String receiptNumber;
    public String status; public String locationName; public Instant receivedAt; public Instant postedAt; public int lineCount;
    public static GoodsReceiptResponse from(GoodsReceipt receipt, int lineCount) {
        GoodsReceiptResponse x = new GoodsReceiptResponse(); x.id=receipt.getId(); x.branchId=receipt.getBranchId();
        x.purchaseOrderId=receipt.getPurchaseOrder().getId(); x.orderNumber=receipt.getPurchaseOrder().getOrderNumber();
        x.receiptNumber=receipt.getReceiptNumber(); x.status=receipt.getStatus(); x.locationName=receipt.getLocation().getName();
        x.receivedAt=receipt.getReceivedAt(); x.postedAt=receipt.getPostedAt(); x.lineCount=lineCount; return x;
    }
}
