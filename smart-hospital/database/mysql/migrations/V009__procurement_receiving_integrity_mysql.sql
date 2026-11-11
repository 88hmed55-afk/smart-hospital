-- Smart Hospital V009: procurement receiving lifecycle and stock posting safeguards.

ALTER TABLE hospital.purchase_order_items
    ADD COLUMN received_quantity DECIMAL(14,3) NOT NULL DEFAULT 0;

ALTER TABLE hospital.purchase_order_items
    ADD CONSTRAINT ck_po_item_received_quantity
    CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity);

CREATE INDEX ix_purchase_orders_branch_status_created
    ON hospital.purchase_orders(branch_id, order_status, created_at DESC);
CREATE INDEX ix_goods_receipts_branch_received
    ON hospital.goods_receipts(branch_id, received_at DESC);
CREATE UNIQUE INDEX uq_goods_receipt_item_receipt_po_line_batch
    ON hospital.goods_receipt_items(receipt_id, purchase_order_item_id, medication_batch_id);
