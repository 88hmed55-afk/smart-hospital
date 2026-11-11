package com.smarthospital.billing.dto;
import com.smarthospital.billing.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
public class PaymentCreateRequest { @NotBlank @jakarta.validation.constraints.Size(min=16,max=80) public String idempotencyKey; @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) public BigDecimal amount; @NotNull public PaymentMethod paymentMethod; @Size(max=120) public String transactionReference; }
