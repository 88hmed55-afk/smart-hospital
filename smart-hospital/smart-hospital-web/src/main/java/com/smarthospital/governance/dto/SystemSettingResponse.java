package com.smarthospital.governance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class SystemSettingResponse {
    public String key;
    public String label;
    public String description;
    public String unit;
    public BigDecimal defaultValue;
    public BigDecimal globalValue;
    public BigDecimal branchValue;
    public BigDecimal effectiveValue;
    public String effectiveSource;
    public BigDecimal minimum;
    public BigDecimal maximum;
    public Instant updatedAt;

    public SystemSettingResponse() { }
}
