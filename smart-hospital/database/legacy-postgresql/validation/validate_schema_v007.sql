\set ON_ERROR_STOP on
DO $$
DECLARE v_tables BIGINT;
BEGIN
  SELECT count(*) INTO v_tables FROM pg_tables WHERE schemaname='hospital';
  IF v_tables <> 63 THEN RAISE EXCEPTION 'Expected 63 hospital tables, found %', v_tables; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_invoice_item_line_math') THEN RAISE EXCEPTION 'Missing invoice arithmetic constraint'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_guard_invoice_item_mutation' AND NOT tgisinternal) THEN RAISE EXCEPTION 'Missing invoice item mutation guard'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_validate_invoice_transition' AND NOT tgisinternal) THEN RAISE EXCEPTION 'Missing invoice transition trigger'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_validate_payment_before_insert' AND NOT tgisinternal) THEN RAISE EXCEPTION 'Missing payment posting trigger'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_prevent_payment_update' AND NOT tgisinternal) THEN RAISE EXCEPTION 'Missing payment immutability trigger'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='hospital' AND indexname='uq_payment_idempotency_key') THEN RAISE EXCEPTION 'Missing payment idempotency index'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='hospital' AND table_name='payments' AND column_name='request_fingerprint') THEN RAISE EXCEPTION 'Missing payment request fingerprint column'; END IF;
  IF to_regclass('hospital.wards') IS NULL OR to_regclass('hospital.beds') IS NULL OR to_regclass('hospital.admissions') IS NULL OR to_regclass('hospital.bed_assignments') IS NULL THEN RAISE EXCEPTION 'Admission schema tables are missing'; END IF;
END $$;
SELECT 'V007 schema validation passed' AS result;
