package com.smarthospital.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class PrescriptionItemRequest {
    @NotNull
    public UUID medicationId;

    @NotBlank
    @Size(max = 80)
    public String dose;

    @Size(max = 60)
    public String route;

    @NotBlank
    @Size(max = 100)
    public String frequency;

    public Integer durationDays;

    @NotNull
    @DecimalMin(value = "0.001")
    @Digits(integer = 11, fraction = 3)
    public BigDecimal quantityPrescribed;

    @Size(max = 500)
    public String instructions;
}
