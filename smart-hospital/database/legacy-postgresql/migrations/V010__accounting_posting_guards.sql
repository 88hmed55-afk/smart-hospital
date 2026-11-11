-- Smart Hospital | V010 | General ledger posting integrity
-- Requires V001-V009. Adds database-side protection for account eligibility.
BEGIN;
SET search_path TO hospital, public;

CREATE OR REPLACE FUNCTION hospital.validate_journal_line_account()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_active BOOLEAN;
    v_allows_posting BOOLEAN;
    v_control BOOLEAN;
BEGIN
    SELECT is_active, allows_posting, is_control_account
      INTO v_active, v_allows_posting, v_control
      FROM hospital.chart_of_accounts
     WHERE id = NEW.account_id AND branch_id = NEW.branch_id
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Journal line account % does not exist in branch %', NEW.account_id, NEW.branch_id;
    END IF;
    IF NOT v_active THEN
        RAISE EXCEPTION 'Inactive account % cannot receive journal postings', NEW.account_id;
    END IF;
    IF NOT v_allows_posting OR v_control THEN
        RAISE EXCEPTION 'Control or non-postable account % cannot receive journal postings', NEW.account_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_journal_line_account ON hospital.journal_lines;
CREATE TRIGGER trg_validate_journal_line_account
BEFORE INSERT OR UPDATE OF account_id, branch_id ON hospital.journal_lines
FOR EACH ROW EXECUTE FUNCTION hospital.validate_journal_line_account();


CREATE OR REPLACE FUNCTION hospital.validate_journal_accounts_before_post()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_invalid_account UUID;
BEGIN
    IF NEW.entry_status = 'POSTED' AND OLD.entry_status = 'DRAFT' THEN
        SELECT jl.account_id INTO v_invalid_account
          FROM hospital.journal_lines jl
          JOIN hospital.chart_of_accounts a
            ON a.id = jl.account_id AND a.branch_id = jl.branch_id
         WHERE jl.journal_entry_id = NEW.id
           AND (a.is_active = FALSE OR a.allows_posting = FALSE OR a.is_control_account = TRUE)
         LIMIT 1;
        IF v_invalid_account IS NOT NULL THEN
            RAISE EXCEPTION 'Journal entry % contains inactive or non-postable account %', NEW.id, v_invalid_account;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_journal_accounts_before_post ON hospital.journal_entries;
CREATE TRIGGER trg_validate_journal_accounts_before_post
BEFORE UPDATE OF entry_status ON hospital.journal_entries
FOR EACH ROW EXECUTE FUNCTION hospital.validate_journal_accounts_before_post();

CREATE INDEX IF NOT EXISTS ix_journal_posted_branch_date
    ON hospital.journal_entries (branch_id, entry_date DESC)
    WHERE entry_status = 'POSTED';
CREATE INDEX IF NOT EXISTS ix_journal_source_lookup
    ON hospital.journal_entries (branch_id, source_module, source_reference_id)
    WHERE source_module IS NOT NULL AND source_reference_id IS NOT NULL;

COMMIT;
