-- Smart Hospital | V006 | Prescription uniqueness and idempotent FEFO pharmacy dispensing
-- MySQL 8.0.38. Apply after V001-V005. Synthetic academic data only.
USE hospital;
-- A prescription may contain a medication only once; quantity changes are not handled by duplicate lines.
ALTER TABLE prescription_items
    ADD CONSTRAINT uq_prescription_item_medication UNIQUE (prescription_id, medication_id);

-- Client retries must resolve to the same immutable dispense operation.
ALTER TABLE dispenses
    ADD COLUMN idempotency_key VARCHAR(80),
    ADD COLUMN request_fingerprint CHAR(64);
ALTER TABLE dispenses
    ADD CONSTRAINT ck_dispense_idempotency_pair CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL)
        OR (idempotency_key IS NOT NULL AND REGEXP_LIKE(request_fingerprint, '^[0-9a-f]{64}$', 'c')
            AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 16 AND 80)
    );
CREATE UNIQUE INDEX uq_dispense_idempotency_key
    ON dispenses(idempotency_key);
-- uq_dispense_stock_movement recreated with MySQL generated columns in 03_constraints.sql.

CREATE INDEX ix_dispense_prescription_status_date
    ON dispenses(prescription_id, status, dispensed_at DESC);
CREATE INDEX ix_stock_balance_location_batch
    ON stock_balances(location_id, batch_id);
