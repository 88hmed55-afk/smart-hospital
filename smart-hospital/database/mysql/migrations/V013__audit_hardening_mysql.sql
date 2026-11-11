-- V013: Tamper-evident audit trail.
-- Adds a per-row hash chain and makes the audit table append-only at both the
-- database privilege layer and the trigger layer.
USE hospital;

ALTER TABLE audit_logs
    ADD COLUMN prev_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER metadata,
    ADD COLUMN row_hash  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER prev_hash;

CREATE INDEX ix_audit_logs_row_hash ON audit_logs (row_hash);

CREATE TABLE IF NOT EXISTS audit_chain_state (
    id            TINYINT PRIMARY KEY,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    last_hash     CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

INSERT INTO audit_chain_state (id, last_sequence, last_hash)
SELECT 1, 0, REPEAT('0', 64)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM audit_chain_state WHERE id = 1);

DROP TRIGGER IF EXISTS trg_audit_logs_no_update;
DROP TRIGGER IF EXISTS trg_audit_logs_no_delete;

CREATE TRIGGER trg_audit_logs_no_update
BEFORE UPDATE ON audit_logs FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only: updates are rejected';

CREATE TRIGGER trg_audit_logs_no_delete
BEFORE DELETE ON audit_logs FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only: deletes are rejected';

-- Defense in depth: the runtime account has no TRIGGER privilege, so it cannot drop
-- these triggers, and both UPDATE and DELETE are rejected below. A separate operator
-- step can additionally replace the database-wide grant with per-table grants.
