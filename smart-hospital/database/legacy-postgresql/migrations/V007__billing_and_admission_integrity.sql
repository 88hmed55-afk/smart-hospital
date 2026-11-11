-- Smart Hospital | V007 | Billing and admission integrity
-- Target: PostgreSQL 16+
-- Adds database-level safeguards to the existing invoice/payment/admission model.
BEGIN;
SET search_path TO hospital, public;

-- Refuse to install stronger constraints over inconsistent historical values.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM invoice_items
         WHERE line_total <> round(quantity * unit_price - line_discount, 2)
            OR line_discount > quantity * unit_price
    ) THEN
        RAISE EXCEPTION 'V007 preflight failed: existing invoice item arithmetic is inconsistent';
    END IF;
END;
$$;

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
        (idempotency_key IS NOT NULL AND length(btrim(idempotency_key)) BETWEEN 16 AND 80
         AND request_fingerprint IS NOT NULL
         AND request_fingerprint ~ '^[0-9a-f]{64}$')
    );
CREATE UNIQUE INDEX uq_payment_idempotency_key
    ON payments (idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_invoices_branch_status_created
    ON invoices (branch_id, status, created_at DESC);
CREATE INDEX ix_invoices_encounter
    ON invoices (encounter_id) WHERE encounter_id IS NOT NULL;

-- Invoice lines may only be added or changed while the parent invoice is DRAFT.
CREATE OR REPLACE FUNCTION hospital.guard_invoice_item_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_invoice_id UUID;
    v_status VARCHAR(20);
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.invoice_id <> NEW.invoice_id THEN
        RAISE EXCEPTION 'Invoice line cannot be moved between invoices';
    END IF;
    v_invoice_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END;
    SELECT status INTO v_status
      FROM hospital.invoices
     WHERE id = v_invoice_id
     FOR UPDATE;
    IF v_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Invoice lines can only be changed while the invoice is DRAFT';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_guard_invoice_item_mutation
BEFORE INSERT OR UPDATE OR DELETE ON invoice_items
FOR EACH ROW EXECUTE FUNCTION hospital.guard_invoice_item_mutation();

-- Verify totals before issuing and verify payment-derived status transitions.
CREATE OR REPLACE FUNCTION hospital.validate_invoice_transition()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_count BIGINT;
    v_lines NUMERIC(16,2);
    v_paid NUMERIC(16,2);
BEGIN
    -- Once issued, financial identity and amount fields remain immutable even
    -- when status changes from ISSUED to PARTIALLY_PAID or PAID.
    IF OLD.status <> 'DRAFT' AND (
        NEW.branch_id IS DISTINCT FROM OLD.branch_id OR
        NEW.invoice_number IS DISTINCT FROM OLD.invoice_number OR
        NEW.patient_id IS DISTINCT FROM OLD.patient_id OR
        NEW.encounter_id IS DISTINCT FROM OLD.encounter_id OR
        NEW.currency_code IS DISTINCT FROM OLD.currency_code OR
        NEW.subtotal IS DISTINCT FROM OLD.subtotal OR
        NEW.discount_amount IS DISTINCT FROM OLD.discount_amount OR
        NEW.tax_amount IS DISTINCT FROM OLD.tax_amount OR
        NEW.total_amount IS DISTINCT FROM OLD.total_amount OR
        NEW.created_by IS DISTINCT FROM OLD.created_by
    ) THEN
        RAISE EXCEPTION 'Issued invoice financial data is immutable';
    END IF;

    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'DRAFT' AND NEW.status = 'ISSUED' THEN
        SELECT count(*), coalesce(sum(line_total), 0)
          INTO v_count, v_lines
          FROM hospital.invoice_items
         WHERE invoice_id = NEW.id;
        IF v_count < 1 THEN
            RAISE EXCEPTION 'An invoice needs at least one line before issue';
        END IF;
        IF v_lines <> NEW.subtotal OR
           NEW.total_amount <> NEW.subtotal - NEW.discount_amount + NEW.tax_amount THEN
            RAISE EXCEPTION 'Invoice totals do not match invoice lines';
        END IF;
        IF NEW.issued_at IS NULL THEN NEW.issued_at := now(); END IF;
        RETURN NEW;
    END IF;

    IF (OLD.status = 'ISSUED' AND NEW.status IN ('PARTIALLY_PAID','PAID')) OR
       (OLD.status = 'PARTIALLY_PAID' AND NEW.status = 'PAID') THEN
        SELECT coalesce(sum(amount), 0) INTO v_paid
          FROM hospital.payments
         WHERE invoice_id = NEW.id AND status = 'POSTED';
        IF NEW.status = 'PARTIALLY_PAID' AND (v_paid <= 0 OR v_paid >= NEW.total_amount) THEN
            RAISE EXCEPTION 'PARTIALLY_PAID status requires a positive payment below the invoice total';
        END IF;
        IF NEW.status = 'PAID' AND v_paid <> NEW.total_amount THEN
            RAISE EXCEPTION 'PAID status requires posted payments equal to the invoice total';
        END IF;
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'Unsupported invoice status transition: % -> %', OLD.status, NEW.status;
END;
$$;
CREATE TRIGGER trg_validate_invoice_transition
BEFORE UPDATE ON invoices
FOR EACH ROW EXECUTE FUNCTION hospital.validate_invoice_transition();

-- Serialize payment posting on the invoice row and prevent overpayment/currency drift.
CREATE OR REPLACE FUNCTION hospital.validate_payment_before_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_status VARCHAR(20);
    v_total NUMERIC(14,2);
    v_currency CHAR(3);
    v_paid NUMERIC(14,2);
BEGIN
    SELECT status, total_amount, currency_code
      INTO v_status, v_total, v_currency
      FROM hospital.invoices
     WHERE id = NEW.invoice_id
     FOR UPDATE;

    IF v_status NOT IN ('ISSUED','PARTIALLY_PAID') THEN
        RAISE EXCEPTION 'Payments may only be posted against ISSUED or PARTIALLY_PAID invoices';
    END IF;
    IF NEW.status <> 'POSTED' THEN
        RAISE EXCEPTION 'New payment records must start in POSTED status';
    END IF;
    IF NEW.idempotency_key IS NULL OR length(btrim(NEW.idempotency_key)) NOT BETWEEN 16 AND 80
       OR NEW.request_fingerprint IS NULL OR NEW.request_fingerprint !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'A valid idempotency key and request fingerprint are required for payment posting';
    END IF;
    IF NEW.currency_code <> v_currency THEN
        RAISE EXCEPTION 'Payment currency must match invoice currency';
    END IF;

    SELECT coalesce(sum(amount), 0) INTO v_paid
      FROM hospital.payments
     WHERE invoice_id = NEW.invoice_id AND status = 'POSTED';
    IF v_paid + NEW.amount > v_total THEN
        RAISE EXCEPTION 'Payment would exceed the invoice outstanding balance';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_validate_payment_before_insert
BEFORE INSERT ON payments
FOR EACH ROW EXECUTE FUNCTION hospital.validate_payment_before_insert();

-- No silent edits/deletes of financial receipts. Refunds/voids require a dedicated
-- reversal workflow and supporting audit/ledger records in a later increment.
CREATE OR REPLACE FUNCTION hospital.prevent_payment_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted payment records are immutable; use a documented reversal workflow';
END;
$$;
CREATE TRIGGER trg_prevent_payment_update
BEFORE UPDATE OR DELETE ON payments
FOR EACH ROW EXECUTE FUNCTION hospital.prevent_payment_mutation();

COMMIT;
