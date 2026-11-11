package com.smarthospital.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class DispenseItemResponse {
    public UUID id;
    public UUID prescriptionItemId;
    public UUID medicationId;
    public String medicationCode;
    public String medicationName;
    public UUID batchId;
    public String batchNumber;
    public LocalDate expiresOn;
    public BigDecimal quantityDispensed;
}
