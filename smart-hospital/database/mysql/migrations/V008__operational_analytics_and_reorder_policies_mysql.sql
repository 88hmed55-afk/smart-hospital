-- Smart Hospital | V008 | Operational analytics and inventory reorder policies
-- Target: MySQL 8.0.38
USE hospital;
-- A branch-aware composite key is needed so reorder policies cannot point to a
-- pharmacy location belonging to a different branch.
ALTER TABLE inventory_locations
    ADD CONSTRAINT uq_inventory_locations_id_branch UNIQUE (id, branch_id);

CREATE TABLE inventory_reorder_policies (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    location_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    medication_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reorder_point       DECIMAL(14,3) NOT NULL,
    minimum_quantity    DECIMAL(14,3) NOT NULL DEFAULT 0,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    updated_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_reorder_policy_location_medication UNIQUE (branch_id, location_id, medication_id),
    CONSTRAINT fk_reorder_policy_location_branch
        FOREIGN KEY (location_id, branch_id)
        REFERENCES inventory_locations (id, branch_id) ON DELETE RESTRICT,
    CONSTRAINT ck_reorder_policy_point CHECK (reorder_point > 0),
    CONSTRAINT ck_reorder_policy_minimum CHECK (minimum_quantity >= 0),
    CONSTRAINT ck_reorder_policy_thresholds CHECK (minimum_quantity <= reorder_point),
    CONSTRAINT fk_inventory_reorder_policies_branch_id_1 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reorder_policies_medication_id_2 FOREIGN KEY (medication_id) REFERENCES medications (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reorder_policies_updated_by_3 FOREIGN KEY (updated_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
CREATE INDEX ix_reorder_policies_branch_active
    ON inventory_reorder_policies (branch_id, location_id, medication_id);

INSERT INTO metric_definitions
    (metric_code, metric_name, source_module, description, unit, aggregation_method, refresh_interval_minutes, is_active)
VALUES
    ('APPOINTMENTS_TODAY', 'Appointments today', 'SCHEDULING', 'Number of appointments starting during the local branch day.', 'count', 'COUNT', 15, TRUE),
    ('ACTIVE_ADMISSIONS', 'Active admissions', 'ADMISSION', 'Admissions currently active in the branch.', 'admission', 'COUNT', 15, TRUE),
    ('OCCUPIED_BEDS', 'Occupied beds', 'ADMISSION', 'Beds currently assigned to active admissions.', 'bed', 'COUNT', 15, TRUE),
    ('VISITS_TODAY', 'Visits today', 'CLINICAL', 'Number of encounters started during the local branch day.', 'count', 'COUNT', 15, TRUE),
    ('BED_OCCUPANCY_PERCENT', 'Bed occupancy', 'ADMISSION', 'Occupied beds divided by active operational beds.', 'percent', 'RATIO', 15, TRUE),
    ('LAB_PENDING_ITEMS', 'Pending laboratory items', 'LABORATORY', 'Laboratory items not yet reviewed/released or cancelled.', 'item', 'COUNT', 10, TRUE),
    ('LAB_TAT_AVG_MINUTES', 'Average laboratory turnaround', 'LABORATORY', 'Average minutes from specimen collection to result release.', 'minute', 'AVG', 30, TRUE),
    ('LOW_STOCK_POLICIES', 'Low stock policies', 'PHARMACY', 'Number of medication/location policies at or below reorder point.', 'policy', 'COUNT', 30, TRUE),
    ('POSTED_REVENUE_TODAY', 'Posted revenue today', 'BILLING', 'Sum of posted payments received during the local branch day.', 'currency', 'SUM', 15, TRUE)
ON DUPLICATE KEY UPDATE metric_name=VALUES(metric_name), source_module=VALUES(source_module),
    description=VALUES(description), unit=VALUES(unit), aggregation_method=VALUES(aggregation_method),
    refresh_interval_minutes=VALUES(refresh_interval_minutes), is_active=VALUES(is_active);

INSERT INTO system_settings (setting_key, branch_id, setting_value, description)
VALUES
    ('analytics.bed_occupancy.warning_percent', NULL, '{"value":85}', 'Warn when operational bed occupancy reaches this percentage.'),
    ('analytics.lab_pending.warning_count', NULL, '{"value":20}', 'Warn when the number of pending lab items reaches this count.'),
    ('analytics.lab_tat.warning_minutes', NULL, '{"value":240}', 'Warn when average lab turnaround reaches this number of minutes.')
ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), description=VALUES(description), updated_at=CURRENT_TIMESTAMP(6);
