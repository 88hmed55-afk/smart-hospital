-- Read-only smoke validation for a database after applying V001 and V002.
-- Run with: psql -d smart_hospital_dev -f validate_schema_v002.sql

DO $$
DECLARE
    required_tables TEXT[] := ARRAY[
        'hospital_branches','departments','staff_members','doctor_profiles','user_accounts',
        'patients','appointments','encounters','lab_orders','lab_results','medications',
        'medication_batches','stock_balances','stock_movements','prescriptions','dispenses',
        'invoices','payments','message_outbox','notifications','audit_logs','operational_alerts',
        'wards','rooms','beds','admissions','bed_assignments','suppliers','purchase_orders',
        'purchase_order_items','purchase_order_approvals','goods_receipts','goods_receipt_items',
        'metric_definitions','metric_snapshots','decision_cards','decision_card_evidence',
        'decision_card_actions','chart_of_accounts','fiscal_periods','journal_entries','journal_lines'
    ];
    t TEXT;
    v_table_count INTEGER;
BEGIN
    FOREACH t IN ARRAY required_tables LOOP
        IF to_regclass(format('hospital.%I', t)) IS NULL THEN
            RAISE EXCEPTION 'Missing required table hospital.%', t;
        END IF;
    END LOOP;

    SELECT count(*) INTO v_table_count
      FROM pg_tables
     WHERE schemaname = 'hospital';
    IF v_table_count <> 61 THEN
        RAISE EXCEPTION 'Expected 61 hospital tables after V002, found %', v_table_count;
    END IF;

    IF EXISTS (SELECT 1 FROM hospital.doctor_profiles WHERE branch_id IS NULL)
       OR EXISTS (SELECT 1 FROM hospital.lab_orders WHERE branch_id IS NULL)
       OR EXISTS (SELECT 1 FROM hospital.prescriptions WHERE branch_id IS NULL)
       OR EXISTS (SELECT 1 FROM hospital.dispenses WHERE branch_id IS NULL) THEN
        RAISE EXCEPTION 'One or more branch backfills are incomplete';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_bed_assignment_no_overlap') THEN
        RAISE EXCEPTION 'Missing bed overlap exclusion constraint';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_admission_one_bed_at_a_time') THEN
        RAISE EXCEPTION 'Missing admission bed timeline exclusion constraint';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_fiscal_period_no_overlap') THEN
        RAISE EXCEPTION 'Missing fiscal period overlap exclusion constraint';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_validate_journal_before_post' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Missing journal posting validation trigger';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_journal_lines_immutable_after_post' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Missing posted journal line immutability trigger';
    END IF;

    RAISE NOTICE 'Smart Hospital V002 schema smoke validation passed.';
END $$;
