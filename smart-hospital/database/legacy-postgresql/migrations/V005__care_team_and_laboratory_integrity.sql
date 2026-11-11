-- Smart Hospital | V005 | Encounter care team and laboratory workflow safeguards
-- PostgreSQL 16+. Apply after V001-V004. Synthetic academic data only.
BEGIN;
SET search_path TO hospital, public;

CREATE TABLE encounter_care_team (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounters(id) ON DELETE RESTRICT,
    staff_id            UUID NOT NULL REFERENCES staff_members(id) ON DELETE RESTRICT,
    care_role           VARCHAR(24) NOT NULL,
    assigned_by         UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at         TIMESTAMPTZ,
    CONSTRAINT ck_care_team_role CHECK (care_role IN ('PRIMARY_NURSE','SUPPORT_NURSE')),
    CONSTRAINT ck_care_team_release_time CHECK (released_at IS NULL OR released_at >= assigned_at)
);
CREATE UNIQUE INDEX uq_encounter_active_care_member
    ON encounter_care_team(encounter_id, staff_id) WHERE released_at IS NULL;
CREATE UNIQUE INDEX uq_encounter_active_primary_nurse
    ON encounter_care_team(encounter_id) WHERE released_at IS NULL AND care_role='PRIMARY_NURSE';
CREATE INDEX ix_encounter_care_team_staff_active
    ON encounter_care_team(staff_id, encounter_id) WHERE released_at IS NULL;

CREATE OR REPLACE FUNCTION hospital.validate_encounter_care_team()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_branch_id UUID;
    v_encounter_status VARCHAR(20);
    v_staff_branch UUID;
    v_staff_status VARCHAR(20);
    v_has_nurse_role BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT branch_id, status INTO v_branch_id, v_encounter_status
          FROM hospital.encounters WHERE id=NEW.encounter_id;
        SELECT branch_id, employment_status INTO v_staff_branch, v_staff_status
          FROM hospital.staff_members WHERE id=NEW.staff_id;
        SELECT EXISTS (
            SELECT 1 FROM hospital.user_accounts ua
            JOIN hospital.user_roles ur ON ur.user_id=ua.id
            JOIN hospital.roles r ON r.id=ur.role_id
            JOIN hospital.user_branch_access uba ON uba.user_id=ua.id AND uba.branch_id=v_branch_id
            WHERE ua.staff_id=NEW.staff_id AND ua.account_status='ACTIVE' AND r.code='NURSE'
        ) INTO v_has_nurse_role;
        IF v_branch_id IS NULL OR v_encounter_status NOT IN ('OPEN','IN_PROGRESS') THEN
            RAISE EXCEPTION 'Care team assignments require an active encounter'
                USING ERRCODE='23514', CONSTRAINT='ck_care_team_active_encounter';
        END IF;
        IF v_staff_branch IS NULL OR v_staff_branch <> v_branch_id OR v_staff_status <> 'ACTIVE' OR NOT v_has_nurse_role THEN
            RAISE EXCEPTION 'Care team member must be an active nurse in the encounter branch'
                USING ERRCODE='23514', CONSTRAINT='ck_care_team_nurse_branch';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Care team assignments cannot be deleted; release the assignment instead'
            USING ERRCODE='55000';
    END IF;

    IF NEW.encounter_id <> OLD.encounter_id
       OR NEW.staff_id <> OLD.staff_id
       OR NEW.care_role <> OLD.care_role
       OR NEW.assigned_by <> OLD.assigned_by
       OR NEW.assigned_at <> OLD.assigned_at
       OR (OLD.released_at IS NOT NULL AND NEW.released_at IS DISTINCT FROM OLD.released_at) THEN
        RAISE EXCEPTION 'Care team identity is immutable; only an active assignment can be released'
            USING ERRCODE='55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_care_team_integrity
    BEFORE INSERT OR UPDATE OR DELETE ON hospital.encounter_care_team
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_encounter_care_team();

-- Prevent duplicate test items in one laboratory order.
ALTER TABLE lab_order_items
    ADD CONSTRAINT uq_lab_order_test UNIQUE (lab_order_id, lab_test_id);

ALTER TABLE lab_results
    ADD CONSTRAINT ck_lab_result_release_requires_review
    CHECK (released_at IS NULL OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL));

CREATE INDEX ix_lab_orders_status_date ON lab_orders(status, ordered_at DESC);
CREATE INDEX ix_lab_order_items_status ON lab_order_items(status, lab_order_id);

ALTER TABLE notifications
    ADD COLUMN source_event_id UUID REFERENCES message_outbox(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uq_notifications_event_recipient
    ON notifications(source_event_id, recipient_user_id) WHERE source_event_id IS NOT NULL;

INSERT INTO roles (code, name, description, is_system_role)
VALUES ('LAB_SUPERVISOR', 'Laboratory Supervisor', 'Reviews and releases laboratory results', TRUE)
ON CONFLICT (code) DO NOTHING;

CREATE OR REPLACE FUNCTION hospital.validate_lab_order_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(20);
    v_doctor UUID;
    v_doctor_active BOOLEAN;
BEGIN
    SELECT status, attending_doctor_id INTO v_status, v_doctor
      FROM hospital.encounters WHERE id=NEW.encounter_id;
    SELECT (dp.is_active AND sm.employment_status='ACTIVE') INTO v_doctor_active
      FROM hospital.doctor_profiles dp
      JOIN hospital.staff_members sm ON sm.id=dp.staff_id
     WHERE dp.staff_id=NEW.ordered_by;
    IF v_status NOT IN ('OPEN','IN_PROGRESS') OR v_doctor IS NULL THEN
        RAISE EXCEPTION 'Laboratory orders require an active encounter'
            USING ERRCODE='23514', CONSTRAINT='ck_lab_order_active_encounter';
    END IF;
    IF NEW.ordered_by <> v_doctor OR v_doctor_active IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'Only the active attending doctor may order laboratory tests'
            USING ERRCODE='23514', CONSTRAINT='ck_lab_order_attending_doctor';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_lab_orders_validate_context
    BEFORE INSERT OR UPDATE OF encounter_id, ordered_by ON hospital.lab_orders
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_lab_order_context();

CREATE OR REPLACE FUNCTION hospital.validate_lab_result_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_branch_id UUID;
    v_staff_branch UUID;
    v_staff_status VARCHAR(20);
    v_item_status VARCHAR(20);
BEGIN
    SELECT e.branch_id, li.status
      INTO v_branch_id, v_item_status
      FROM hospital.lab_order_items li
      JOIN hospital.lab_orders lo ON lo.id=li.lab_order_id
      JOIN hospital.encounters e ON e.id=lo.encounter_id
     WHERE li.id=NEW.lab_order_item_id;
    IF v_branch_id IS NULL OR v_item_status NOT IN ('COLLECTED','PROCESSING','RESULTED','REVIEWED') THEN
        RAISE EXCEPTION 'A laboratory result requires a collected or processing specimen'
            USING ERRCODE='23514', CONSTRAINT='ck_lab_result_specimen_state';
    END IF;
    IF NEW.performed_by IS NOT NULL THEN
        SELECT branch_id, employment_status INTO v_staff_branch, v_staff_status
          FROM hospital.staff_members WHERE id=NEW.performed_by;
        IF v_staff_branch IS DISTINCT FROM v_branch_id OR v_staff_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'Result performer must be active in the laboratory order branch'
                USING ERRCODE='23514', CONSTRAINT='ck_lab_result_performer_branch';
        END IF;
    END IF;
    IF NEW.reviewed_by IS NOT NULL THEN
        SELECT branch_id, employment_status INTO v_staff_branch, v_staff_status
          FROM hospital.staff_members WHERE id=NEW.reviewed_by;
        IF v_staff_branch IS DISTINCT FROM v_branch_id OR v_staff_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'Result reviewer must be active in the laboratory order branch'
                USING ERRCODE='23514', CONSTRAINT='ck_lab_result_reviewer_branch';
        END IF;
        IF NEW.reviewed_by = NEW.performed_by THEN
            RAISE EXCEPTION 'A result must be reviewed by a different staff member than its performer'
                USING ERRCODE='23514', CONSTRAINT='ck_lab_result_independent_review';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_lab_results_validate_context
    BEFORE INSERT OR UPDATE OF lab_order_item_id, performed_by, reviewed_by ON hospital.lab_results
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_lab_result_context();

COMMIT;
