package com.smarthospital.pharmacy.dto;

import com.smarthospital.pharmacy.entity.Dispense;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DispenseResponse {
    public UUID id;
    public UUID prescriptionId;
    public UUID inventoryLocationId;
    public UUID dispensedByStaffId;
    public String status;
    public Instant dispensedAt;
    public String idempotencyKey;
    public List<DispenseItemResponse> items;

    public static DispenseResponse from(Dispense dispense, List<DispenseItemResponse> items) {
        DispenseResponse response = new DispenseResponse();
        response.id = dispense.getId();
        response.prescriptionId = dispense.getPrescriptionId();
        response.inventoryLocationId = dispense.getInventoryLocation().getId();
        response.dispensedByStaffId = dispense.getDispensedBy().getId();
        response.status = dispense.getStatus().name();
        response.dispensedAt = dispense.getDispensedAt();
        response.idempotencyKey = dispense.getIdempotencyKey();
        response.items = items;
        return response;
    }
}
