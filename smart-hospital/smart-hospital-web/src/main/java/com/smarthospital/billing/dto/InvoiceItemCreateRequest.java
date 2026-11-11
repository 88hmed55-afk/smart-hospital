package com.smarthospital.billing.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.UUID;
public class InvoiceItemCreateRequest { @NotNull public UUID serviceId; @NotNull @DecimalMin("0.001") @DecimalMax("1000.000") @Digits(integer=11,fraction=3) public BigDecimal quantity; }
