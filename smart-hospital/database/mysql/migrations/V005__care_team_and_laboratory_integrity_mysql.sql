-- Smart Hospital | V005 | Encounter care team and laboratory workflow safeguards
-- PostgreSQL 16+. Apply after V001-V004. Synthetic academic data only.
USE hospital;
CREATE TABLE encounter_care_team (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    staff_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    care_role           VARCHAR(24) NOT NULL,
    assigned_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    released_at         DATETIME(6),
    CONSTRAINT ck_care_team_role CHECK (care_role IN ('PRIMARY_NURSE','SUPPORT_NURSE')),
    CONSTRAINT ck_care_team_release_time CHECK (released_at IS NULL OR released_at >= assigned_at),
    CONSTRAINT fk_encounter_care_team_encounter_id_1 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounter_care_team_staff_id_2 FOREIGN KEY (staff_id) REFERENCES staff_members (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounter_care_team_assigned_by_3 FOREIGN KEY (assigned_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
-- uq_encounter_active_care_member recreated with MySQL generated columns in 03_constraints.sql.

-- uq_encounter_active_primary_nurse recreated with MySQL generated columns in 03_constraints.sql.

CREATE INDEX ix_encounter_care_team_staff_active
    ON encounter_care_team(staff_id, encounter_id);

-- Prevent duplicate test items in one laboratory order.
ALTER TABLE lab_order_items
    ADD CONSTRAINT uq_lab_order_test UNIQUE (lab_order_id, lab_test_id);

ALTER TABLE lab_results
    ADD CONSTRAINT ck_lab_result_release_requires_review
    CHECK (released_at IS NULL OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL));

CREATE INDEX ix_lab_orders_status_date ON lab_orders(status, ordered_at DESC);
CREATE INDEX ix_lab_order_items_status ON lab_order_items(status, lab_order_id);

ALTER TABLE notifications ADD COLUMN source_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
CREATE UNIQUE INDEX uq_notifications_event_recipient
    ON notifications(source_event_id, recipient_user_id);

INSERT IGNORE INTO roles (code, name, description, is_system_role)
VALUES ('LAB_SUPERVISOR', 'Laboratory Supervisor', 'Reviews and releases laboratory results', TRUE)
;

ALTER TABLE notifications ADD CONSTRAINT fk_notifications_source_event_id_inline FOREIGN KEY (source_event_id) REFERENCES message_outbox (id) ON DELETE RESTRICT;
