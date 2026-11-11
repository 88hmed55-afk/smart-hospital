package com.smarthospital.accounting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "journal_lines")
public class JournalLine {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "journal_entry_id", nullable = false, updatable = false) private UUID journalEntryId;
    @Column(name = "branch_id", nullable = false, updatable = false) private UUID branchId;
    @Column(name = "line_number", nullable = false, updatable = false) private short lineNumber;
    @Column(name = "account_id", nullable = false, updatable = false) private UUID accountId;
    @Column(name = "debit_amount", nullable = false, precision = 16, scale = 2, updatable = false) private BigDecimal debitAmount;
    @Column(name = "credit_amount", nullable = false, precision = 16, scale = 2, updatable = false) private BigDecimal creditAmount;
    @Column(name = "line_memo", length = 500, updatable = false) private String lineMemo;
    protected JournalLine() { }
    public static JournalLine create(UUID entryId, UUID branchId, short number, UUID accountId,
                                     BigDecimal debit, BigDecimal credit, String memo) {
        JournalLine line = new JournalLine(); line.id = UUID.randomUUID(); line.journalEntryId = entryId;
        line.branchId = branchId; line.lineNumber = number; line.accountId = accountId;
        line.debitAmount = debit; line.creditAmount = credit; line.lineMemo = memo; return line;
    }
    public UUID getId() { return id; }
    public UUID getJournalEntryId() { return journalEntryId; }
    public UUID getBranchId() { return branchId; }
    public short getLineNumber() { return lineNumber; }
    public UUID getAccountId() { return accountId; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getLineMemo() { return lineMemo; }
}
