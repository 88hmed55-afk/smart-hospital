\set ON_ERROR_STOP on
DO $$
BEGIN
    IF (SELECT count(*) FROM hospital.inventory_reorder_policies
         WHERE branch_id='10000000-0000-4000-8000-000000000001') < 2 THEN
        RAISE EXCEPTION 'Expected two synthetic pharmacy reorder policies';
    END IF;
    IF (SELECT count(*) FROM hospital.metric_definitions WHERE is_active=TRUE AND metric_code IN (
        'APPOINTMENTS_TODAY','ACTIVE_ADMISSIONS','OCCUPIED_BEDS','VISITS_TODAY','BED_OCCUPANCY_PERCENT','LAB_PENDING_ITEMS',
        'LAB_TAT_AVG_MINUTES','LOW_STOCK_POLICIES','POSTED_REVENUE_TODAY')) <> 9 THEN
        RAISE EXCEPTION 'Active operational metric definitions are missing';
    END IF;
END $$;
SELECT 'V008 analytics fixtures validation passed' AS result;
