-- Smart Hospital | V004 | Clinical workflow integrity
-- Apply only after V001, V002 and V003.
-- This migration adds database-level cross-table safeguards for clinical records.
BEGIN;
SET search_path TO hospital, public;

-- An encounter must be associated with a patient who has an explicit branch access row.
ALTER TABLE hospital.encounters
    ADD CONSTRAINT fk_encounter_patient_branch_access
    FOREIGN KEY (patient_id, branch_id)
    REFERENCES hospital.patient_branch_access(patient_id, branch_id)
    ON DELETE RESTRICT;

CREATE INDEX ix_encounters_branch_status_started
    ON hospital.encounters(branch_id, status, started_at DESC);
CREATE INDEX ix_vital_signs_encounter_measured
    ON hospital.vital_signs(encounter_id, measured_at DESC);
CREATE INDEX ix_clinical_notes_encounter_created
    ON hospital.clinical_notes(encounter_id, created_at DESC);
CREATE INDEX ix_encounter_diagnoses_encounter
    ON hospital.encounter_diagnoses(encounter_id, is_primary DESC, recorded_at);
CREATE INDEX ix_diagnosis_catalog_description
    ON hospital.diagnosis_catalog (lower(description));

ALTER TABLE hospital.vital_signs
    ADD CONSTRAINT ck_vitals_blood_pressure_pair
        CHECK ((systolic_mmhg IS NULL) = (diastolic_mmhg IS NULL)
               AND (systolic_mmhg IS NULL OR systolic_mmhg > diastolic_mmhg)),
    ADD CONSTRAINT ck_vitals_at_least_one_measurement
        CHECK (temperature_c IS NOT NULL OR systolic_mmhg IS NOT NULL
               OR diastolic_mmhg IS NOT NULL OR pulse_bpm IS NOT NULL
               OR respiratory_rate IS NOT NULL OR oxygen_saturation IS NOT NULL
               OR weight_kg IS NOT NULL OR height_cm IS NOT NULL);

-- Validate branch, doctor, department, patient access and appointment linkage.
CREATE OR REPLACE FUNCTION hospital.validate_encounter_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_doctor_branch UUID;
    v_doctor_department UUID;
    v_department_branch UUID;
    v_branch_active BOOLEAN;
    v_patient_status VARCHAR(20);
BEGIN
    SELECT hb.is_active INTO v_branch_active
      FROM hospital.hospital_branches hb WHERE hb.id = NEW.branch_id;
    IF v_branch_active IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'Encounter branch must be active'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_branch_active';
    END IF;

    SELECT p.patient_status INTO v_patient_status
      FROM hospital.patients p WHERE p.id = NEW.patient_id;
    IF v_patient_status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'Only active patient records may receive a new encounter'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_patient_active';
    END IF;

    SELECT sm.branch_id, sm.department_id
      INTO v_doctor_branch, v_doctor_department
      FROM hospital.doctor_profiles dp
      JOIN hospital.staff_members sm ON sm.id = dp.staff_id
     WHERE dp.staff_id = NEW.attending_doctor_id
       AND dp.is_active = TRUE
       AND sm.employment_status = 'ACTIVE';

    IF v_doctor_branch IS NULL THEN
        RAISE EXCEPTION 'Encounter requires an active doctor profile'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_active_doctor';
    END IF;

    SELECT d.branch_id INTO v_department_branch
      FROM hospital.departments d
     WHERE d.id = NEW.department_id AND d.is_active = TRUE;

    IF v_department_branch IS NULL THEN
        RAISE EXCEPTION 'Encounter requires an active department'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_active_department';
    END IF;

    IF v_doctor_branch <> NEW.branch_id OR v_department_branch <> NEW.branch_id THEN
        RAISE EXCEPTION 'Encounter doctor, department and branch must match'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_branch_scope';
    END IF;

    IF v_doctor_department IS DISTINCT FROM NEW.department_id THEN
        RAISE EXCEPTION 'Attending doctor must be assigned to the encounter department'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_doctor_department';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (
            SELECT 1 FROM hospital.patient_branch_access pba
             WHERE pba.patient_id = NEW.patient_id
               AND pba.branch_id = NEW.branch_id
               AND pba.revoked_at IS NULL
        ) THEN
            RAISE EXCEPTION 'Patient does not have active access in encounter branch'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_patient_branch_access_active';
        END IF;
    END IF;

    IF NEW.appointment_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM hospital.appointments a
             WHERE a.id = NEW.appointment_id
               AND a.branch_id = NEW.branch_id
               AND a.patient_id = NEW.patient_id
               AND a.doctor_staff_id = NEW.attending_doctor_id
               AND a.department_id = NEW.department_id
               AND a.status = 'CHECKED_IN'
        ) THEN
            RAISE EXCEPTION 'Linked appointment must match encounter and be checked in'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_encounter_appointment_match';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_encounters_validate_scope
    BEFORE INSERT OR UPDATE OF branch_id, patient_id, appointment_id, department_id, attending_doctor_id
    ON hospital.encounters
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_encounter_scope();

-- Clinical observations are append-only. Corrections are captured as new observations.
CREATE OR REPLACE FUNCTION hospital.prevent_clinical_observation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Clinical observations are append-only; record a correction as a new entry'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_vital_signs_append_only
    BEFORE UPDATE OR DELETE ON hospital.vital_signs
    FOR EACH ROW EXECUTE FUNCTION hospital.prevent_clinical_observation_mutation();

-- Diagnosis identity is immutable, but its clinical status may be reviewed and changed.
CREATE OR REPLACE FUNCTION hospital.protect_encounter_diagnosis_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Encounter diagnoses cannot be deleted; change the clinical status to RULED_OUT when appropriate'
            USING ERRCODE = '55000';
    END IF;
    IF NEW.encounter_id <> OLD.encounter_id
       OR NEW.diagnosis_id <> OLD.diagnosis_id
       OR NEW.recorded_by <> OLD.recorded_by
       OR NEW.is_primary <> OLD.is_primary
       OR NEW.recorded_at <> OLD.recorded_at THEN
        RAISE EXCEPTION 'Encounter diagnosis identity fields are immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_encounter_diagnosis_identity
    BEFORE UPDATE OR DELETE ON hospital.encounter_diagnoses
    FOR EACH ROW EXECUTE FUNCTION hospital.protect_encounter_diagnosis_identity();


-- Verify author/recorder branch and encounter context for append-only clinical data.
CREATE OR REPLACE FUNCTION hospital.validate_clinical_actor_branch()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_encounter_branch UUID;
    v_staff_branch UUID;
    v_staff_status VARCHAR(20);
BEGIN
    SELECT e.branch_id INTO v_encounter_branch
      FROM hospital.encounters e WHERE e.id = NEW.encounter_id;

    IF TG_TABLE_NAME = 'vital_signs' THEN
        SELECT sm.branch_id, sm.employment_status INTO v_staff_branch, v_staff_status
          FROM hospital.staff_members sm WHERE sm.id = NEW.measured_by;
    ELSIF TG_TABLE_NAME = 'clinical_notes' THEN
        SELECT sm.branch_id, sm.employment_status INTO v_staff_branch, v_staff_status
          FROM hospital.staff_members sm WHERE sm.id = NEW.author_staff_id;
    ELSE
        SELECT sm.branch_id, sm.employment_status INTO v_staff_branch, v_staff_status
          FROM hospital.staff_members sm WHERE sm.id = NEW.recorded_by;
    END IF;

    IF v_encounter_branch IS NULL OR v_staff_branch IS NULL
       OR v_encounter_branch <> v_staff_branch OR v_staff_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Clinical actor must be active in the encounter branch'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_clinical_actor_branch';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_vital_signs_actor_branch
    BEFORE INSERT ON hospital.vital_signs
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_clinical_actor_branch();
CREATE TRIGGER trg_clinical_notes_actor_branch
    BEFORE INSERT ON hospital.clinical_notes
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_clinical_actor_branch();
CREATE TRIGGER trg_encounter_diagnoses_actor_branch
    BEFORE INSERT ON hospital.encounter_diagnoses
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_clinical_actor_branch();

-- Draft notes may be edited before signing. Signed notes cannot be changed or deleted.
CREATE OR REPLACE FUNCTION hospital.protect_signed_clinical_notes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_parent_status VARCHAR(16);
    v_parent_encounter UUID;
    v_parent_type VARCHAR(24);
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Clinical notes cannot be deleted; use an amendment'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.note_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Signed or amended clinical notes are immutable; create an amendment'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.encounter_id <> OLD.encounter_id
       OR NEW.author_staff_id <> OLD.author_staff_id
       OR NEW.note_type <> OLD.note_type
       OR NEW.amended_from_id IS DISTINCT FROM OLD.amended_from_id
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Clinical note identity fields cannot be changed'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.amended_from_id IS NOT NULL THEN
        SELECT note_status, encounter_id, note_type
          INTO v_parent_status, v_parent_encounter, v_parent_type
          FROM hospital.clinical_notes WHERE id = NEW.amended_from_id;
        IF v_parent_status NOT IN ('SIGNED','AMENDED')
           OR v_parent_encounter <> NEW.encounter_id
           OR v_parent_type <> NEW.note_type THEN
            RAISE EXCEPTION 'An amendment must reference a signed note of the same encounter and type'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_clinical_note_amendment_parent';
        END IF;
    END IF;
    IF NEW.note_status = 'AMENDED' AND NEW.amended_from_id IS NULL THEN
        RAISE EXCEPTION 'An AMENDED note must reference the original signed note'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_clinical_note_amended_source';
    END IF;
    IF NEW.note_status = 'SIGNED' AND NEW.amended_from_id IS NOT NULL THEN
        RAISE EXCEPTION 'An amendment must finish with AMENDED status'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_clinical_note_amendment_status';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_clinical_notes_immutable
    BEFORE UPDATE OR DELETE ON hospital.clinical_notes
    FOR EACH ROW EXECUTE FUNCTION hospital.protect_signed_clinical_notes();

CREATE OR REPLACE FUNCTION hospital.validate_clinical_note_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_parent_status VARCHAR(16);
    v_parent_encounter UUID;
    v_parent_type VARCHAR(24);
BEGIN
    IF NEW.amended_from_id IS NOT NULL THEN
        SELECT note_status, encounter_id, note_type
          INTO v_parent_status, v_parent_encounter, v_parent_type
          FROM hospital.clinical_notes WHERE id = NEW.amended_from_id;
        IF v_parent_status NOT IN ('SIGNED','AMENDED')
           OR v_parent_encounter <> NEW.encounter_id
           OR v_parent_type <> NEW.note_type THEN
            RAISE EXCEPTION 'An amendment must reference a signed note of the same encounter and type'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_clinical_note_amendment_parent';
        END IF;
    END IF;
    IF NEW.note_status = 'AMENDED' AND NEW.amended_from_id IS NULL THEN
        RAISE EXCEPTION 'An AMENDED note must reference the original signed note'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_clinical_note_amended_source';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_clinical_notes_validate_insert
    BEFORE INSERT ON hospital.clinical_notes
    FOR EACH ROW EXECUTE FUNCTION hospital.validate_clinical_note_insert();

COMMENT ON TABLE hospital.encounters IS
    'Clinical encounters. Branch/patient linkage and clinical actor scope are protected by V004 triggers and constraints.';
COMMENT ON TABLE hospital.vital_signs IS
    'Append-only vital-sign observations; corrections are represented by new observations.';
COMMENT ON TABLE hospital.clinical_notes IS
    'Draft notes may be edited before signing; signed notes are immutable and corrected through amendments.';

COMMIT;
