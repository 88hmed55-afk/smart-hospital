BEGIN;
SET search_path TO hospital, public;

CREATE OR REPLACE FUNCTION hospital.reject_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only; create a correction event instead of changing history.'
        USING ERRCODE = '55000';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_logs_append_only ON hospital.audit_logs;
CREATE TRIGGER trg_audit_logs_append_only
BEFORE UPDATE OR DELETE ON hospital.audit_logs
FOR EACH ROW EXECUTE FUNCTION hospital.reject_audit_log_mutation();

DROP TRIGGER IF EXISTS trg_audit_logs_no_truncate ON hospital.audit_logs;
CREATE TRIGGER trg_audit_logs_no_truncate
BEFORE TRUNCATE ON hospital.audit_logs
FOR EACH STATEMENT EXECUTE FUNCTION hospital.reject_audit_log_mutation();

CREATE INDEX IF NOT EXISTS ix_audit_branch_time
    ON hospital.audit_logs(branch_id, occurred_at DESC);

REVOKE UPDATE, DELETE, TRUNCATE ON hospital.audit_logs FROM PUBLIC;

COMMIT;
