-- Final schema validation after V001, V002 and V003.
-- Run with: psql -d smart_hospital -v ON_ERROR_STOP=1 -f validate_schema_v003.sql
DO $$
DECLARE
    v_table_count INTEGER;
BEGIN
    SELECT count(*) INTO v_table_count FROM pg_tables WHERE schemaname = 'hospital';
    IF v_table_count <> 62 THEN
        RAISE EXCEPTION 'Expected 62 hospital tables after V003, found %', v_table_count;
    END IF;

    IF to_regclass('hospital.patient_branch_access') IS NULL THEN
        RAISE EXCEPTION 'Missing hospital.patient_branch_access';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'hospital.patient_branch_access'::regclass
          AND conname = 'uq_patient_branch_access'
    ) THEN
        RAISE EXCEPTION 'Missing patient/branch uniqueness constraint';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'hospital'
          AND indexname = 'ix_patient_branch_access_branch_patient'
    ) THEN
        RAISE EXCEPTION 'Missing active patient/branch lookup index';
    END IF;

    RAISE NOTICE 'Smart Hospital V003 validation passed: % hospital tables.', v_table_count;
END $$;
