DO $$
DECLARE table_count INTEGER;
BEGIN
    SELECT count(*) INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'hospital' AND table_type = 'BASE TABLE';
    IF table_count <> 64 THEN
        RAISE EXCEPTION 'Expected 64 hospital tables after V009, found %', table_count;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema='hospital' AND table_name='purchase_order_items'
           AND column_name='received_quantity' AND data_type='numeric'
    ) THEN RAISE EXCEPTION 'purchase_order_items.received_quantity is missing'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname='trg_validate_purchase_order_transition' AND NOT tgisinternal
    ) THEN RAISE EXCEPTION 'Purchase order transition trigger is missing'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname='trg_validate_goods_receipt_posting' AND NOT tgisinternal
    ) THEN RAISE EXCEPTION 'Goods receipt posting trigger is missing'; END IF;
END $$;
