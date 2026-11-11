package com.smarthospital.laboratory.dto;

import com.smarthospital.laboratory.entity.AbnormalFlag;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class LabResultCreateRequest {
    @Size(max = 4000)
    public String resultText;

    @Digits(integer = 11, fraction = 5)
    public BigDecimal resultNumeric;

    @Size(max = 40)
    public String resultUnit;

    @Size(max = 120)
    public String referenceRange;

    public AbnormalFlag abnormalFlag = AbnormalFlag.UNKNOWN;
}
