DO $$
DECLARE
    row_trigger_count integer;
    truncate_trigger_count integer;
BEGIN
    IF to_regclass('hospital.audit_logs') IS NULL THEN
        RAISE EXCEPTION 'V011 validation failed: hospital.audit_logs is missing.';
    END IF;

    SELECT count(*) INTO row_trigger_count
      FROM pg_trigger
     WHERE tgrelid = 'hospital.audit_logs'::regclass
       AND tgname = 'trg_audit_logs_append_only'
       AND NOT tgisinternal;
    IF row_trigger_count <> 1 THEN
        RAISE EXCEPTION 'V011 validation failed: append-only row trigger is missing.';
    END IF;

    SELECT count(*) INTO truncate_trigger_count
      FROM pg_trigger
     WHERE tgrelid = 'hospital.audit_logs'::regclass
       AND tgname = 'trg_audit_logs_no_truncate'
       AND NOT tgisinternal;
    IF truncate_trigger_count <> 1 THEN
        RAISE EXCEPTION 'V011 validation failed: truncate protection is missing.';
    END IF;

    IF to_regprocedure('hospital.reject_audit_log_mutation()') IS NULL THEN
        RAISE EXCEPTION 'V011 validation failed: audit mutation guard function is missing.';
    END IF;

    IF to_regclass('hospital.ix_audit_branch_time') IS NULL THEN
        RAISE EXCEPTION 'V011 validation failed: branch/time audit index is missing.';
    END IF;
END $$;
