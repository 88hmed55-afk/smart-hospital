\set ON_ERROR_STOP on
DO $$
DECLARE v_tables INTEGER;
BEGIN
    SELECT count(*) INTO v_tables FROM pg_tables WHERE schemaname='hospital';
    IF v_tables <> 63 THEN
        RAISE EXCEPTION 'Expected 63 hospital tables after V006; found %', v_tables;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='hospital' AND table_name='dispenses' AND column_name='idempotency_key') THEN
        RAISE EXCEPTION 'dispenses.idempotency_key is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='hospital' AND table_name='dispenses' AND column_name='request_fingerprint') THEN
        RAISE EXCEPTION 'dispenses.request_fingerprint is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='hospital' AND indexname='uq_dispense_idempotency_key') THEN
        RAISE EXCEPTION 'Dispense idempotency unique index is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_dispenses_validate_context' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Pharmacy dispense context trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_dispense_items_validate_context' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Dispense item context trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_dispense_stock_movements_append_only' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'Dispense stock-ledger append-only trigger is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_prescription_item_medication') THEN
        RAISE EXCEPTION 'Prescription duplicate medication constraint is missing';
    END IF;
END $$;
SELECT 'V006 schema validation passed: FEFO dispensing guards, idempotency and stock-ledger integrity are installed.' AS validation_result;
