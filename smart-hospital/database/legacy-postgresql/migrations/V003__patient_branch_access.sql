-- Smart Hospital | V003 | Branch-scoped patient access
-- Apply only after V001 and V002 on a development database.
BEGIN;
SET search_path TO hospital, public;

CREATE TABLE patient_branch_access (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    branch_id       UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    access_reason   VARCHAR(160) NOT NULL DEFAULT 'CARE',
    granted_by      UUID REFERENCES user_accounts(id),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT uq_patient_branch_access UNIQUE (patient_id, branch_id),
    CONSTRAINT ck_patient_branch_access_reason CHECK (length(btrim(access_reason)) > 0),
    CONSTRAINT ck_patient_branch_access_revocation CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
CREATE INDEX ix_patient_branch_access_branch_patient
    ON patient_branch_access(branch_id, patient_id)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE patient_branch_access IS
    'Explicit branch-level authorization to access a shared patient master record.';

COMMIT;
