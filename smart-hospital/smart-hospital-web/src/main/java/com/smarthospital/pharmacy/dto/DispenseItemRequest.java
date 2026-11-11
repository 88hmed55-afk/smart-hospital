package com.smarthospital.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public class DispenseItemRequest {
    @NotNull
    public UUID prescriptionItemId;

    @NotNull
    @DecimalMin(value = "0.001")
    @Digits(integer = 11, fraction = 3)
    public BigDecimal quantity;
}
