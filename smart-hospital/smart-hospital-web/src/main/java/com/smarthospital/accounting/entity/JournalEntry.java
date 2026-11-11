package com.smarthospital.accounting.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "journal_entries")
public class JournalEntry {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "branch_id", nullable = false, updatable = false) private UUID branchId;
    @Column(name = "fiscal_period_id", nullable = false, updatable = false) private UUID fiscalPeriodId;
    @Column(name = "entry_number", nullable = false, updatable = false, length = 40) private String entryNumber;
    @Column(name = "entry_date", nullable = false, updatable = false) private LocalDate entryDate;
    @Column(name = "description", nullable = false, length = 1000) private String description;
    @Column(name = "source_module", length = 48, updatable = false) private String sourceModule;
    @Column(name = "source_reference_id", updatable = false) private UUID sourceReferenceId;
    @Enumerated(EnumType.STRING) @Column(name = "entry_status", nullable = false, length = 12) private JournalStatus entryStatus;
    @Column(name = "reversal_of_entry_id", updatable = false) private UUID reversalOfEntryId;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "posted_at") private Instant postedAt;
    protected JournalEntry() { }
    public static JournalEntry draft(UUID branchId, UUID periodId, String entryNumber, LocalDate date,
                                     String description, String sourceModule, UUID sourceReferenceId,
                                     UUID reversalOfEntryId, UUID createdBy) {
        JournalEntry entry = new JournalEntry();
        entry.id = UUID.randomUUID(); entry.branchId = branchId; entry.fiscalPeriodId = periodId;
        entry.entryNumber = entryNumber; entry.entryDate = date; entry.description = description;
        entry.sourceModule = sourceModule; entry.sourceReferenceId = sourceReferenceId;
        entry.reversalOfEntryId = reversalOfEntryId; entry.createdBy = createdBy;
        entry.entryStatus = JournalStatus.DRAFT; entry.createdAt = Instant.now();
        return entry;
    }
    public void post(UUID userId) {
        if (entryStatus != JournalStatus.DRAFT) throw new IllegalStateException("Only draft journals can be posted.");
        entryStatus = JournalStatus.POSTED; postedBy = userId; postedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public UUID getFiscalPeriodId() { return fiscalPeriodId; }
    public String getEntryNumber() { return entryNumber; }
    public LocalDate getEntryDate() { return entryDate; }
    public String getDescription() { return description; }
    public String getSourceModule() { return sourceModule; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }
    public JournalStatus getEntryStatus() { return entryStatus; }
    public UUID getReversalOfEntryId() { return reversalOfEntryId; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getPostedBy() { return postedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPostedAt() { return postedAt; }
}
