-- Smart Hospital | V004 | Clinical workflow integrity
-- Apply only after V001, V002 and V003.
-- This migration adds database-level cross-table safeguards for clinical records.
USE hospital;
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
    ON hospital.diagnosis_catalog (description);

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

-- Clinical observations are append-only. Corrections are captured as new observations.

-- Diagnosis identity is immutable, but its clinical status may be reviewed and changed.

-- Verify author/recorder branch and encounter context for append-only clinical data.

-- Draft notes may be edited before signing. Signed notes cannot be changed or deleted.

