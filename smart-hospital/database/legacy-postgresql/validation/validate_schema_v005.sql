\set ON_ERROR_STOP on
DO $$
DECLARE v_tables INTEGER;
BEGIN
    SELECT count(*) INTO v_tables FROM pg_tables WHERE schemaname='hospital';
    IF v_tables <> 63 THEN
        RAISE EXCEPTION 'Expected 63 hospital tables after V005; found %', v_tables;
    END IF;
    IF to_regclass('hospital.encounter_care_team') IS NULL THEN
        RAISE EXCEPTION 'encounter_care_team table is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='hospital' AND table_name='notifications' AND column_name='source_event_id') THEN
        RAISE EXCEPTION 'notifications.source_event_id is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_care_team_integrity' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Care team integrity trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_lab_orders_validate_context' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Laboratory order context trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_lab_results_validate_context' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Laboratory result context trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='hospital' AND indexname='uq_encounter_active_primary_nurse') THEN
        RAISE EXCEPTION 'Primary nurse uniqueness index is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM hospital.roles WHERE code='LAB_SUPERVISOR') THEN
        RAISE EXCEPTION 'LAB_SUPERVISOR role is missing';
    END IF;
END $$;
SELECT 'V005 schema validation passed: care team, laboratory constraints and message event de-duplication are present.' AS validation_result;
