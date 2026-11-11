package com.smarthospital.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Aggregated operational indicators only; contains no patient-identifying information. */
public class DashboardResponse {
    public UUID branchId;
    public String branchName;
    public String currencyCode;
    public Instant periodStart;
    public Instant periodEnd;
    public long appointmentsToday;
    public long visitsToday;
    public long checkedInAppointments;
    public long activeAdmissions;
    public long labTurnaroundSamples;
    public long operationalBeds;
    public long occupiedBeds;
    public BigDecimal bedOccupancyPercent;
    public long pendingLabItems;
    public BigDecimal averageLabTurnaroundMinutes;
    public long lowStockMedicationPolicies;
    public BigDecimal postedRevenueToday;
    public long openDecisionCards;

    public DashboardResponse() { }
}
