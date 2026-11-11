package com.smarthospital.governance.dto;

import java.time.Instant;

/** Result of an independent re-computation of the tamper-evident audit hash chain. */
public class AuditIntegrityReport {
    public Instant checkedAt;
    public long totalRows;
    public long chainRows;
    public long unchainedRows;
    public long verifiedRows;
    public long legacyRows;
    public long hashMismatches;
    public long brokenLinks;
    public boolean chainIntact;
    public String storedHeadHash;
    public String recomputedHeadHash;
    public long storedSequence;
    public String message;
}
