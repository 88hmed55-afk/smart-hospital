package com.smarthospital.pharmacy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PrescriptionItemResponse {
    public UUID id;
    public UUID medicationId;
    public String medicationCode;
    public String medicationName;
    public String brandName;
    public String strength;
    public String dosageForm;
    public String dispensingUnit;
    public String dose;
    public String route;
    public String frequency;
    public Integer durationDays;
    public BigDecimal quantityPrescribed;
    public BigDecimal quantityDispensed;
    public BigDecimal quantityRemaining;
    public String instructions;
}
