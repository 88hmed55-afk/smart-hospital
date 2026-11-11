package com.smarthospital.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Operational decision card. JSON fields are serialized JSON strings from MySQL JSON columns. */
public class DecisionCardResponse {
    public UUID id;
    public String cardCode;
    public String category;
    public String severity;
    public String status;
    public String title;
    public String problemStatement;
    public String analysisSummary;
    public String recommendation;
    public Instant evidenceWindowStart;
    public Instant evidenceWindowEnd;
    public BigDecimal observedValue;
    public BigDecimal thresholdValue;
    public String unit;
    public BigDecimal confidencePercent;
    public String assumptionsJson;
    public String expectedImpactJson;
    public Instant generatedAt;

    public DecisionCardResponse() { }
}
