USE hospital;

CREATE INDEX ix_audit_branch_time
    ON hospital.audit_logs(branch_id, occurred_at DESC);
