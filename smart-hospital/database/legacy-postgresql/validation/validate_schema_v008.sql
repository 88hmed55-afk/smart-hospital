\set ON_ERROR_STOP on
DO $$
DECLARE v_tables BIGINT;
BEGIN
    SELECT count(*) INTO v_tables FROM pg_tables WHERE schemaname='hospital';
    IF v_tables <> 64 THEN
        RAISE EXCEPTION 'Expected 64 hospital tables after V008, found %', v_tables;
    END IF;
    IF to_regclass('hospital.inventory_reorder_policies') IS NULL THEN
        RAISE EXCEPTION 'Missing inventory_reorder_policies';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_reorder_policy_location_branch') THEN
        RAISE EXCEPTION 'Missing branch-aware inventory reorder location constraint';
    END IF;
    IF (SELECT count(*) FROM hospital.metric_definitions WHERE metric_code IN (
        'APPOINTMENTS_TODAY','ACTIVE_ADMISSIONS','OCCUPIED_BEDS','VISITS_TODAY','BED_OCCUPANCY_PERCENT','LAB_PENDING_ITEMS',
        'LAB_TAT_AVG_MINUTES','LOW_STOCK_POLICIES','POSTED_REVENUE_TODAY')) <> 9 THEN
        RAISE EXCEPTION 'Operational metric definitions are incomplete';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM hospital.system_settings WHERE setting_key='analytics.bed_occupancy.warning_percent' AND branch_id IS NULL) THEN
        RAISE EXCEPTION 'Missing global bed occupancy threshold';
    END IF;
END $$;
SELECT 'V008 schema validation passed' AS result;
