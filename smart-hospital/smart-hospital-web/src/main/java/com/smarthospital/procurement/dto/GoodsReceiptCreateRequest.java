package com.smarthospital.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class GoodsReceiptCreateRequest {
    @NotNull public UUID branchId;
    @NotNull public UUID inventoryLocationId;
    @Size(max=100) public String supplierDeliveryReference;
    @Size(max=1000) public String notes;
    @NotEmpty @Size(max=80) public List<@Valid GoodsReceiptLineRequest> items;
}
