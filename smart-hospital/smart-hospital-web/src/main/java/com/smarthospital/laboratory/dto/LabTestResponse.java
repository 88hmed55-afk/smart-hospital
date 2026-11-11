package com.smarthospital.laboratory.dto;

import com.smarthospital.laboratory.entity.LabTestCatalog;
import java.math.BigDecimal;
import java.util.UUID;

public class LabTestResponse {
    public UUID id;
    public String code;
    public String name;
    public String specimenType;
    public String resultUnit;
    public String referenceRange;
    public BigDecimal standardPrice;
    public String currencyCode;
    public Integer expectedMinutes;

    public static LabTestResponse from(LabTestCatalog test) {
        LabTestResponse response = new LabTestResponse();
        response.id = test.getId();
        response.code = test.getCode();
        response.name = test.getName();
        response.specimenType = test.getSpecimenType();
        response.resultUnit = test.getResultUnit();
        response.referenceRange = test.getReferenceRange();
        response.standardPrice = test.getStandardPrice();
        response.currencyCode = test.getCurrencyCode();
        response.expectedMinutes = test.getExpectedMinutes();
        return response;
    }
}
