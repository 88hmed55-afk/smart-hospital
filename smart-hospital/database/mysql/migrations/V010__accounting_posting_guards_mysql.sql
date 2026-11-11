-- Smart Hospital | V010 | General ledger posting integrity
-- Requires V001-V009. Adds database-side protection for account eligibility.
USE hospital;

CREATE INDEX ix_journal_posted_branch_date
    ON hospital.journal_entries (branch_id, entry_date DESC);
CREATE INDEX ix_journal_source_lookup
    ON hospital.journal_entries (branch_id, source_module, source_reference_id);
