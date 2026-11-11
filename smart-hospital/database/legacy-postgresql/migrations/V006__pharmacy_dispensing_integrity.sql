-- Smart Hospital | V006 | Prescription uniqueness and idempotent FEFO pharmacy dispensing
-- PostgreSQL 16+. Apply after V001-V005. Synthetic academic data only.
BEGIN;
SET search_path TO hospital, public;

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
        OR (idempotency_key IS NOT NULL AND request_fingerprint ~ '^[0-9a-f]{64}$'
            AND length(btrim(idempotency_key)) BETWEEN 16 AND 80)
    );
CREATE UNIQUE INDEX uq_dispense_idempotency_key
    ON dispenses(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_dispense_stock_movement
    ON stock_movements(reference_id, batch_id)
    WHERE reference_type='DISPENSE' AND movement_type='DISPENSE';
CREATE INDEX ix_dispense_prescription_status_date
    ON dispenses(prescription_id, status, dispensed_at DESC);
CREATE INDEX ix_stock_balance_location_batch
    ON stock_balances(location_id, batch_id);

CREATE OR REPLACE FUNCTION hospital.validate_pharmacy_dispense()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_prescription_status VARCHAR(20);
    v_branch_id UUID;
    v_patient_id UUID;
    v_location_branch UUID;
    v_location_type VARCHAR(20);
    v_location_active BOOLEAN;
    v_staff_branch UUID;
    v_staff_status VARCHAR(20);
    v_department_branch UUID;
    v_department_type VARCHAR(32);
    v_department_active BOOLEAN;
    v_has_pharmacist_access BOOLEAN;
    v_item_count BIGINT;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Dispense headers are retained as immutable transaction history'
            USING ERRCODE='55000';
    END IF;

    IF TG_OP='UPDATE' AND OLD.status='POSTED' THEN
        RAISE EXCEPTION 'Posted dispenses are immutable; use an approved reversal workflow'
            USING ERRCODE='55000';
    END IF;

    SELECT p.status, e.branch_id, e.patient_id
      INTO v_prescription_status, v_branch_id, v_patient_id
      FROM hospital.prescriptions p
      JOIN hospital.encounters e ON e.id=p.encounter_id
     WHERE p.id=NEW.prescription_id;
    IF v_branch_id IS NULL OR v_prescription_status NOT IN ('ACTIVE','PARTIALLY_DISPENSED') THEN
        RAISE EXCEPTION 'Only an active prescription in an active encounter may be dispensed'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_active_prescription';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM hospital.patient_branch_access pba
         WHERE pba.patient_id=v_patient_id AND pba.branch_id=v_branch_id AND pba.revoked_at IS NULL
    ) THEN
        RAISE EXCEPTION 'The patient is not authorized at the prescription branch'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_patient_branch_access';
    END IF;

    SELECT branch_id, location_type, is_active
      INTO v_location_branch, v_location_type, v_location_active
      FROM hospital.inventory_locations WHERE id=NEW.inventory_location_id;
    IF v_location_branch IS DISTINCT FROM v_branch_id OR v_location_type <> 'PHARMACY' OR v_location_active IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'Dispense location must be an active pharmacy location in the encounter branch'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_pharmacy_location';
    END IF;

    SELECT sm.branch_id, sm.employment_status, d.branch_id, d.department_type, d.is_active
      INTO v_staff_branch, v_staff_status, v_department_branch, v_department_type, v_department_active
      FROM hospital.staff_members sm
      JOIN hospital.departments d ON d.id=sm.department_id
     WHERE sm.id=NEW.dispensed_by;
    SELECT EXISTS (
        SELECT 1 FROM hospital.user_accounts ua
        JOIN hospital.user_roles ur ON ur.user_id=ua.id
        JOIN hospital.roles r ON r.id=ur.role_id
        JOIN hospital.user_branch_access uba ON uba.user_id=ua.id AND uba.branch_id=v_branch_id
        WHERE ua.staff_id=NEW.dispensed_by AND ua.account_status='ACTIVE' AND r.code='PHARMACIST'
    ) INTO v_has_pharmacist_access;
    IF v_staff_branch IS DISTINCT FROM v_branch_id OR v_staff_status <> 'ACTIVE'
       OR v_department_branch IS DISTINCT FROM v_branch_id OR v_department_type <> 'PHARMACY'
       OR v_department_active IS DISTINCT FROM TRUE OR NOT v_has_pharmacist_access THEN
        RAISE EXCEPTION 'Dispensing staff must be an active pharmacist assigned to the pharmacy department and branch'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_pharmacist_scope';
    END IF;

    IF NEW.status='POSTED' THEN
        SELECT count(*) INTO v_item_count FROM hospital.dispense_items WHERE dispense_id=NEW.id;
        IF v_item_count=0 THEN
            RAISE EXCEPTION 'A dispense cannot be posted without item allocations'
                USING ERRCODE='23514', CONSTRAINT='ck_dispense_requires_items';
        END IF;
        IF EXISTS (
            SELECT 1
              FROM hospital.dispense_items di
              LEFT JOIN hospital.stock_movements sm
                ON sm.reference_type='DISPENSE'
               AND sm.reference_id=NEW.id
               AND sm.batch_id=di.batch_id
               AND sm.location_id=NEW.inventory_location_id
               AND sm.performed_by IN (SELECT ua.id FROM hospital.user_accounts ua WHERE ua.staff_id=NEW.dispensed_by)
               AND sm.movement_type='DISPENSE'
               AND sm.quantity_delta=-di.quantity_dispensed
             WHERE di.dispense_id=NEW.id AND sm.id IS NULL
        ) OR (
            SELECT count(*) FROM hospital.stock_movements
             WHERE reference_type='DISPENSE' AND reference_id=NEW.id AND movement_type='DISPENSE'
        ) <> v_item_count THEN
            RAISE EXCEPTION 'Every dispense allocation must have a matching negative stock movement'
                USING ERRCODE='23514', CONSTRAINT='ck_dispense_stock_ledger_match';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dispenses_validate_context
    BEFORE INSERT OR UPDATE OR DELETE ON hospital.dispenses
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_pharmacy_dispense();

CREATE OR REPLACE FUNCTION hospital.validate_dispense_item_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_dispense_status VARCHAR(16);
    v_prescription_id UUID;
    v_location_id UUID;
    v_item_prescription UUID;
    v_item_medication UUID;
    v_batch_medication UUID;
    v_batch_expiry DATE;
    v_balance_exists BOOLEAN;
    v_quantity_prescribed NUMERIC(14,3);
    v_quantity_already_allocated NUMERIC(14,3);
BEGIN
    IF TG_OP='DELETE' OR TG_OP='UPDATE' THEN
        RAISE EXCEPTION 'Dispense allocations are immutable; use an approved reversal workflow'
            USING ERRCODE='55000';
    END IF;

    SELECT status, prescription_id, inventory_location_id
      INTO v_dispense_status, v_prescription_id, v_location_id
      FROM hospital.dispenses WHERE id=NEW.dispense_id;
    IF v_dispense_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Dispense allocations can only be added to a draft dispense'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_item_draft_header';
    END IF;

    SELECT prescription_id, medication_id, quantity_prescribed
      INTO v_item_prescription, v_item_medication, v_quantity_prescribed
      FROM hospital.prescription_items WHERE id=NEW.prescription_item_id
      FOR UPDATE;
    SELECT medication_id, expires_on
      INTO v_batch_medication, v_batch_expiry
      FROM hospital.medication_batches WHERE id=NEW.batch_id;
    IF v_item_prescription IS DISTINCT FROM v_prescription_id
       OR v_item_medication IS NULL OR v_batch_medication IS DISTINCT FROM v_item_medication THEN
        RAISE EXCEPTION 'Dispense item, prescription and medication batch do not match'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_item_medication_match';
    END IF;
    IF v_batch_expiry < CURRENT_DATE THEN
        RAISE EXCEPTION 'Expired medication batches cannot be dispensed'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_item_not_expired';
    END IF;
    SELECT COALESCE(sum(di.quantity_dispensed), 0) INTO v_quantity_already_allocated
      FROM hospital.dispense_items di
      JOIN hospital.dispenses dh ON dh.id=di.dispense_id
     WHERE di.prescription_item_id=NEW.prescription_item_id
       AND dh.status IN ('DRAFT','POSTED');
    IF v_quantity_prescribed IS NULL
       OR v_quantity_already_allocated + NEW.quantity_dispensed > v_quantity_prescribed THEN
        RAISE EXCEPTION 'Dispensed quantity cannot exceed the prescribed quantity'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_item_prescription_quantity';
    END IF;
    SELECT EXISTS (
        SELECT 1 FROM hospital.stock_balances sb
         WHERE sb.location_id=v_location_id AND sb.batch_id=NEW.batch_id
    ) INTO v_balance_exists;
    IF NOT v_balance_exists THEN
        RAISE EXCEPTION 'A stock balance for the pharmacy location and batch is required'
            USING ERRCODE='23514', CONSTRAINT='ck_dispense_item_stock_balance';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dispense_items_validate_context
    BEFORE INSERT OR UPDATE OR DELETE ON hospital.dispense_items
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_dispense_item_context();

CREATE OR REPLACE FUNCTION hospital.protect_posted_dispense_stock_movement()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.reference_type='DISPENSE' THEN
            RAISE EXCEPTION 'Dispense stock ledger entries are append-only'
                USING ERRCODE='55000';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.reference_type='DISPENSE' OR NEW.reference_type='DISPENSE' THEN
        RAISE EXCEPTION 'Dispense stock ledger entries are append-only'
            USING ERRCODE='55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dispense_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON hospital.stock_movements
    FOR EACH ROW EXECUTE FUNCTION hospital.protect_posted_dispense_stock_movement();

COMMIT;
