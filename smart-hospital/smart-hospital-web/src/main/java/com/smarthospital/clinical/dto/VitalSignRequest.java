package com.smarthospital.clinical.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class VitalSignRequest {
    @DecimalMin("25.0") @DecimalMax("45.0") public BigDecimal temperatureC;
    @Min(20) @Max(300) public Integer systolicMmhg;
    @Min(10) @Max(200) public Integer diastolicMmhg;
    @Min(1) @Max(300) public Integer pulseBpm;
    @Min(1) @Max(100) public Integer respiratoryRate;
    @DecimalMin("0.0") @DecimalMax("100.0") public BigDecimal oxygenSaturation;
    @DecimalMin("0.01") @DecimalMax("1000.0") public BigDecimal weightKg;
    @DecimalMin("0.1") @DecimalMax("300.0") public BigDecimal heightCm;
    @Size(max = 500) public String notes;
}
