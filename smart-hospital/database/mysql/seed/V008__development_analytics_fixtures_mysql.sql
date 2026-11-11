-- DEVELOPMENT ONLY: synthetic thresholds for the sample pharmacy inventory.
USE hospital;
INSERT INTO inventory_reorder_policies
    (branch_id, location_id, medication_id, reorder_point, minimum_quantity, is_active)
VALUES
    ('10000000-0000-4000-8000-000000000001',
     '60000000-0000-4000-8000-000000000001',
     '70000000-0000-4000-8000-000000000001', 125.000, 25.000, TRUE),
    ('10000000-0000-4000-8000-000000000001',
     '60000000-0000-4000-8000-000000000001',
     '70000000-0000-4000-8000-000000000002', 65.000, 15.000, TRUE)
ON DUPLICATE KEY UPDATE reorder_point=VALUES(reorder_point),
    minimum_quantity=VALUES(minimum_quantity), is_active=VALUES(is_active),
    updated_at=CURRENT_TIMESTAMP(6);
