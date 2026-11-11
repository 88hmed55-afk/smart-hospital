-- V022: ensure purchase_order_items.received_quantity exists on databases that
-- were built from the aggregate 01_schema.sql before the column was added.
--
-- The PurchaseOrderItem entity writes received_quantity on every goods receipt,
-- so a schema missing the column makes both the purchase-order list and the
-- receiving workflow fail with "Unknown column 'received_quantity'". V009 adds
-- the column for versioned installs; this migration is written defensively so
-- it is a no-op when the column is already present.

SET @has_received_quantity := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'hospital'
      AND TABLE_NAME = 'purchase_order_items'
      AND COLUMN_NAME = 'received_quantity'
);

SET @ddl := IF(@has_received_quantity = 0,
    'ALTER TABLE hospital.purchase_order_items ADD COLUMN received_quantity DECIMAL(14,3) NOT NULL DEFAULT 0',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_check := (
    SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = 'hospital'
      AND CONSTRAINT_NAME = 'ck_po_item_received_quantity'
);

SET @ddl := IF(@has_check = 0,
    'ALTER TABLE hospital.purchase_order_items ADD CONSTRAINT ck_po_item_received_quantity CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
