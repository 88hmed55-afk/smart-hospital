package com.smarthospital.procurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class PurchaseOrderItemCreateRequest {
    @NotNull public UUID medicationId;
    @NotNull @DecimalMin("0.001") public BigDecimal quantity;
    @Size(max = 32) public String unitOfMeasure;
    @NotNull @DecimalMin("0.00") public BigDecimal unitCost;
    @NotNull @DecimalMin("0.00") public BigDecimal lineDiscount;
}
