-- Smart Hospital | V003 | Branch-scoped patient access
-- Apply only after V001 and V002 on a development database.
USE hospital;
CREATE TABLE patient_branch_access (

    id              CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    patient_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    access_reason   VARCHAR(160) NOT NULL DEFAULT 'CARE',
    granted_by      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    granted_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at      DATETIME(6),
    CONSTRAINT uq_patient_branch_access UNIQUE (patient_id, branch_id),
    CONSTRAINT ck_patient_branch_access_reason CHECK (CHAR_LENGTH(TRIM(access_reason)) > 0),
    CONSTRAINT ck_patient_branch_access_revocation CHECK (revoked_at IS NULL OR revoked_at >= granted_at),
    CONSTRAINT fk_patient_branch_access_patient_id_1 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_branch_access_branch_id_2 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_branch_access_granted_by_3 FOREIGN KEY (granted_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_patient_branch_access_branch_patient
    ON patient_branch_access(branch_id, patient_id);
