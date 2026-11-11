package com.smarthospital.accounting.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "fiscal_periods")
public class FiscalPeriod {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "branch_id", nullable = false, updatable = false) private UUID branchId;
    @Column(name = "period_code", nullable = false, length = 24) private String periodCode;
    @Column(name = "period_name", nullable = false, length = 120) private String periodName;
    @Column(name = "starts_on", nullable = false) private LocalDate startsOn;
    @Column(name = "ends_on", nullable = false) private LocalDate endsOn;
    @Column(name = "period_status", nullable = false, length = 12) private String periodStatus;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "closed_at") private Instant closedAt;
    protected FiscalPeriod() { }
    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public String getPeriodCode() { return periodCode; }
    public String getPeriodName() { return periodName; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public String getPeriodStatus() { return periodStatus; }
}
