package com.smarthospital.procurement.dto;

import com.smarthospital.procurement.entity.PurchaseOrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public class PurchaseOrderItemResponse {
    public UUID id; public UUID medicationId; public String medicationCode; public String medicationName;
    public String description; public int lineNumber; public BigDecimal orderedQuantity; public BigDecimal receivedQuantity;
    public BigDecimal remainingQuantity; public String unitOfMeasure; public BigDecimal unitCost; public BigDecimal lineDiscount; public BigDecimal lineTotal;
    public static PurchaseOrderItemResponse from(PurchaseOrderItem item) {
        PurchaseOrderItemResponse x = new PurchaseOrderItemResponse(); x.id=item.getId(); x.medicationId=item.getMedicationId();
        x.medicationCode=item.getMedication().getCode(); x.medicationName=item.getMedication().getGenericName(); x.description=item.getDescription();
        x.lineNumber=item.getLineNumber(); x.orderedQuantity=item.getOrderedQuantity(); x.receivedQuantity=item.getReceivedQuantity();
        x.remainingQuantity=item.getRemainingQuantity(); x.unitOfMeasure=item.getUnitOfMeasure(); x.unitCost=item.getUnitCost();
        x.lineDiscount=item.getLineDiscount(); x.lineTotal=item.getLineTotal(); return x;
    }
}
