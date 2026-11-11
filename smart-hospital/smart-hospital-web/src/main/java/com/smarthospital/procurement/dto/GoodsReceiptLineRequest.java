package com.smarthospital.procurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class GoodsReceiptLineRequest {
    @NotNull public UUID purchaseOrderItemId;
    @NotNull @DecimalMin("0.000") public BigDecimal acceptedQuantity;
    @NotNull @DecimalMin("0.000") public BigDecimal rejectedQuantity;
    @Size(max=80) public String batchNumber;
    @Size(max=160) public String manufacturer;
    public LocalDate manufacturedOn;
    public LocalDate expiresOn;
    @Size(max=300) public String rejectionReason;
}
