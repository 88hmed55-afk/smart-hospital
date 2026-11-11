package com.smarthospital.pharmacy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class StockAvailabilityResponse {
    public UUID medicationId;
    public String medicationCode;
    public String medicationName;
    public String dispensingUnit;
    public BigDecimal quantityAvailable;
}
