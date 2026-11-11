package com.smarthospital.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class JournalEntryResponse {
    public UUID id;
    public UUID branchId;
    public String entryNumber;
    public LocalDate entryDate;
    public String description;
    public String sourceModule;
    public UUID sourceReferenceId;
    public String status;
    public UUID reversalOfEntryId;
    public UUID createdBy;
    public UUID postedBy;
    public Instant createdAt;
    public Instant postedAt;
    public BigDecimal totalDebit;
    public BigDecimal totalCredit;
    public List<Line> lines;
    public static class Line {
        public short lineNumber;
        public String accountCode;
        public String accountName;
        public BigDecimal debitAmount;
        public BigDecimal creditAmount;
        public String memo;
    }
}
