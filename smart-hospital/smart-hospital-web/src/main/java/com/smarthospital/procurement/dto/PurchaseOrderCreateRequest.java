package com.smarthospital.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PurchaseOrderCreateRequest {
    @NotNull public UUID branchId;
    @NotNull public UUID supplierId;
    public LocalDate expectedDeliveryOn;
    @Size(max = 1000) public String notes;
    @NotEmpty @Size(max = 40) public List<@Valid PurchaseOrderItemCreateRequest> items;
}
