-- Smart Hospital | V007 | Billing and admission integrity
-- Target: MySQL 8.0.38
-- Adds database-level safeguards to the existing invoice/payment/admission model.
USE hospital;
-- Refuse to install stronger constraints over inconsistent historical values.
-- PostgreSQL DO-block omitted; corresponding MySQL validation is documented separately.

ALTER TABLE invoice_items
    ADD CONSTRAINT ck_invoice_item_line_math
    CHECK (line_discount <= quantity * unit_price
       AND line_total = round(quantity * unit_price - line_discount, 2));

ALTER TABLE payments
    ADD COLUMN idempotency_key VARCHAR(80),
    ADD COLUMN request_fingerprint CHAR(64);
ALTER TABLE payments
    ADD CONSTRAINT ck_payment_idempotency_pair CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL) OR
        (idempotency_key IS NOT NULL AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 16 AND 80
         AND request_fingerprint IS NOT NULL
         AND REGEXP_LIKE(request_fingerprint, '^[0-9a-f]{64}$', 'c'))
    );
CREATE UNIQUE INDEX uq_payment_idempotency_key
    ON payments (idempotency_key);

CREATE INDEX ix_invoices_branch_status_created
    ON invoices (branch_id, status, created_at DESC);
CREATE INDEX ix_invoices_encounter
    ON invoices (encounter_id);

-- Invoice lines may only be added or changed while the parent invoice is DRAFT.

-- Verify totals before issuing and verify payment-derived status transitions.

-- Serialize payment posting on the invoice row and prevent overpayment/currency drift.

-- No silent edits/deletes of financial receipts. Refunds/voids require a dedicated
-- reversal workflow and supporting audit/ledger records in a later increment.
