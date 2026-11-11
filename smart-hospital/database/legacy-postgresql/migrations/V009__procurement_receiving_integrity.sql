-- Smart Hospital V009: procurement receiving lifecycle and stock posting safeguards.
BEGIN;

ALTER TABLE hospital.purchase_order_items
    ADD COLUMN received_quantity NUMERIC(14,3) NOT NULL DEFAULT 0;

ALTER TABLE hospital.purchase_order_items
    ADD CONSTRAINT ck_po_item_received_quantity
    CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity);

CREATE INDEX ix_purchase_orders_branch_status_created
    ON hospital.purchase_orders(branch_id, order_status, created_at DESC);
CREATE INDEX ix_goods_receipts_branch_received
    ON hospital.goods_receipts(branch_id, received_at DESC);
CREATE UNIQUE INDEX uq_goods_receipt_item_receipt_po_line_batch
    ON hospital.goods_receipt_items(receipt_id, purchase_order_item_id, medication_batch_id)
    WHERE medication_batch_id IS NOT NULL;

CREATE OR REPLACE FUNCTION hospital.guard_purchase_order_item_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE current_status VARCHAR(24);
BEGIN
    SELECT order_status INTO current_status
      FROM hospital.purchase_orders
     WHERE id = OLD.purchase_order_id
     FOR UPDATE;
    IF current_status IS NULL THEN
        RAISE EXCEPTION 'Purchase order not found';
    END IF;
    IF current_status <> 'DRAFT' AND (
        NEW.purchase_order_id IS DISTINCT FROM OLD.purchase_order_id OR
        NEW.line_number IS DISTINCT FROM OLD.line_number OR
        NEW.medication_id IS DISTINCT FROM OLD.medication_id OR
        NEW.item_description_snapshot IS DISTINCT FROM OLD.item_description_snapshot OR
        NEW.ordered_quantity IS DISTINCT FROM OLD.ordered_quantity OR
        NEW.unit_of_measure IS DISTINCT FROM OLD.unit_of_measure OR
        NEW.unit_cost IS DISTINCT FROM OLD.unit_cost OR
        NEW.line_discount IS DISTINCT FROM OLD.line_discount OR
        NEW.line_total IS DISTINCT FROM OLD.line_total
    ) THEN
        RAISE EXCEPTION 'Only draft purchase order lines may be edited';
    END IF;
    IF NEW.received_quantity < OLD.received_quantity THEN
        RAISE EXCEPTION 'Received quantity cannot decrease';
    END IF;
    IF NEW.received_quantity > NEW.ordered_quantity THEN
        RAISE EXCEPTION 'Received quantity cannot exceed ordered quantity';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_guard_purchase_order_item_mutation
BEFORE UPDATE ON hospital.purchase_order_items
FOR EACH ROW EXECUTE FUNCTION hospital.guard_purchase_order_item_mutation();

CREATE OR REPLACE FUNCTION hospital.validate_purchase_order_transition()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.order_status = OLD.order_status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.order_status = 'DRAFT' AND NEW.order_status IN ('SUBMITTED','CANCELLED')) OR
        (OLD.order_status = 'SUBMITTED' AND NEW.order_status IN ('APPROVED','REJECTED','CANCELLED')) OR
        (OLD.order_status = 'APPROVED' AND NEW.order_status IN ('ORDERED','CANCELLED')) OR
        (OLD.order_status = 'ORDERED' AND NEW.order_status IN ('PARTIALLY_RECEIVED','RECEIVED')) OR
        (OLD.order_status = 'PARTIALLY_RECEIVED' AND NEW.order_status = 'RECEIVED')
    ) THEN
        RAISE EXCEPTION 'Invalid purchase order status transition: % -> %', OLD.order_status, NEW.order_status;
    END IF;
    IF NEW.order_status IN ('PARTIALLY_RECEIVED','RECEIVED') AND NOT EXISTS (
        SELECT 1 FROM hospital.purchase_order_items poi
         WHERE poi.purchase_order_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'A purchase order without lines cannot be received';
    END IF;
    IF NEW.order_status = 'RECEIVED' AND EXISTS (
        SELECT 1 FROM hospital.purchase_order_items poi
         WHERE poi.purchase_order_id = NEW.id
           AND poi.received_quantity < poi.ordered_quantity
    ) THEN
        RAISE EXCEPTION 'Purchase order cannot be marked received until every line is fully received';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_validate_purchase_order_transition
BEFORE UPDATE OF order_status ON hospital.purchase_orders
FOR EACH ROW EXECUTE FUNCTION hospital.validate_purchase_order_transition();

CREATE OR REPLACE FUNCTION hospital.guard_goods_receipt_item_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE receipt_state VARCHAR(16);
BEGIN
    SELECT receipt_status INTO receipt_state
      FROM hospital.goods_receipts
     WHERE id = COALESCE(NEW.receipt_id, OLD.receipt_id)
     FOR UPDATE;
    IF receipt_state IS NULL THEN
        RAISE EXCEPTION 'Goods receipt not found';
    END IF;
    IF receipt_state <> 'DRAFT' THEN
        RAISE EXCEPTION 'Goods receipt items are immutable after posting';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_guard_goods_receipt_item_mutation
BEFORE INSERT OR UPDATE OR DELETE ON hospital.goods_receipt_items
FOR EACH ROW EXECUTE FUNCTION hospital.guard_goods_receipt_item_mutation();

CREATE OR REPLACE FUNCTION hospital.validate_goods_receipt_posting()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE line_count BIGINT;
BEGIN
    IF NEW.receipt_status = OLD.receipt_status THEN RETURN NEW; END IF;
    IF OLD.receipt_status <> 'DRAFT' OR NEW.receipt_status NOT IN ('POSTED','VOID') THEN
        RAISE EXCEPTION 'Invalid goods receipt status transition';
    END IF;
    IF NEW.receipt_status = 'POSTED' THEN
        SELECT count(*) INTO line_count FROM hospital.goods_receipt_items WHERE receipt_id = NEW.id;
        IF line_count = 0 OR NEW.posted_by IS NULL OR NEW.posted_at IS NULL THEN
            RAISE EXCEPTION 'A posted goods receipt requires lines, poster and posting timestamp';
        END IF;
        IF EXISTS (
            SELECT 1 FROM hospital.goods_receipt_items gri
             WHERE gri.receipt_id = NEW.id
               AND gri.accepted_quantity > 0
               AND gri.medication_batch_id IS NULL
        ) THEN
            RAISE EXCEPTION 'Accepted receipt quantities require a medication batch';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_validate_goods_receipt_posting
BEFORE UPDATE OF receipt_status ON hospital.goods_receipts
FOR EACH ROW EXECUTE FUNCTION hospital.validate_goods_receipt_posting();

CREATE OR REPLACE FUNCTION hospital.guard_goods_receipt_stock_movement()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.movement_type = 'RECEIPT' AND NEW.reference_type = 'GOODS_RECEIPT' THEN
        IF NOT EXISTS (
            SELECT 1
              FROM hospital.goods_receipts gr
              JOIN hospital.goods_receipt_items gri ON gri.receipt_id = gr.id
             WHERE gr.id = NEW.reference_id
               AND gr.receipt_status = 'POSTED'
               AND gr.inventory_location_id = NEW.location_id
               AND gri.medication_batch_id = NEW.batch_id
               AND gri.accepted_quantity = NEW.quantity_delta
        ) THEN
            RAISE EXCEPTION 'Receipt stock movement must match a posted goods receipt item';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_guard_goods_receipt_stock_movement
BEFORE INSERT ON hospital.stock_movements
FOR EACH ROW EXECUTE FUNCTION hospital.guard_goods_receipt_stock_movement();

COMMIT;
