\set ON_ERROR_STOP on
DO $$
DECLARE
    v_table_count INTEGER;
    v_missing INTEGER;
BEGIN
    SELECT count(*) INTO v_table_count
      FROM pg_tables WHERE schemaname = 'hospital';
    IF v_table_count <> 62 THEN
        RAISE EXCEPTION 'Expected 62 hospital tables after V004; found %', v_table_count;
    END IF;

    SELECT count(*) INTO v_missing
      FROM (VALUES
        ('trg_encounters_validate_scope'),
        ('trg_vital_signs_append_only'),
        ('trg_vital_signs_actor_branch'),
        ('trg_clinical_notes_actor_branch'),
        ('trg_encounter_diagnoses_actor_branch'),
        ('trg_clinical_notes_immutable'),
        ('trg_clinical_notes_validate_insert'),
        ('trg_encounter_diagnosis_identity')
      ) expected(trigger_name)
      WHERE NOT EXISTS (
          SELECT 1 FROM pg_trigger t
          JOIN pg_class c ON c.oid = t.tgrelid
          JOIN pg_namespace n ON n.oid = c.relnamespace
          WHERE n.nspname = 'hospital'
            AND t.tgname = expected.trigger_name
            AND NOT t.tgisinternal
      );
    IF v_missing <> 0 THEN
        RAISE EXCEPTION 'V004 expected triggers are missing: %', v_missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_encounter_patient_branch_access'
           AND conrelid = 'hospital.encounters'::regclass
    ) THEN
        RAISE EXCEPTION 'V004 composite patient/branch foreign key is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname='hospital' AND indexname='ix_encounters_branch_status_started'
    ) THEN
        RAISE EXCEPTION 'V004 encounter operational index is missing';
    END IF;

    IF (SELECT count(*) FROM pg_constraint
         WHERE conrelid='hospital.vital_signs'::regclass
           AND conname IN ('ck_vitals_blood_pressure_pair','ck_vitals_at_least_one_measurement')) <> 2 THEN
        RAISE EXCEPTION 'V004 vital-sign integrity constraints are missing';
    END IF;
END $$;

SELECT 'V004 clinical workflow integrity validation passed.' AS validation_result;
