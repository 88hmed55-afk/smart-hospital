-- Smart Hospital | V002 | Admissions, procurement, smart operations, accounting
-- Target: MySQL 8.0.38
-- Apply only after V001__hospital_core.sql has completed successfully.
-- All operational and clinical test data must be synthetic.
USE hospital;
-- -----------------------------------------------------------------------------
-- 0. Composite keys and integrity hardening for branch-aware relationships
-- -----------------------------------------------------------------------------
ALTER TABLE departments
    ADD CONSTRAINT uq_departments_id_branch UNIQUE (id, branch_id);

ALTER TABLE staff_members
    ADD CONSTRAINT uq_staff_members_id_branch UNIQUE (id, branch_id);

ALTER TABLE inventory_locations
    ADD CONSTRAINT uq_inventory_locations_id_branch UNIQUE (id, branch_id);

ALTER TABLE encounters
    ADD CONSTRAINT uq_encounters_id_branch_patient UNIQUE (id, branch_id, patient_id),
    ADD CONSTRAINT uq_encounters_id_branch UNIQUE (id, branch_id);

-- Doctor profiles repeat the staff branch key so appointment/encounter references
-- can enforce branch consistency with composite foreign keys.
ALTER TABLE doctor_profiles ADD COLUMN branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
UPDATE doctor_profiles dp JOIN staff_members sm ON sm.id=dp.staff_id SET dp.branch_id=sm.branch_id;
ALTER TABLE doctor_profiles MODIFY branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
ALTER TABLE doctor_profiles
    ADD CONSTRAINT uq_doctor_profiles_staff_branch UNIQUE (staff_id, branch_id),
    ADD CONSTRAINT fk_doctor_profiles_staff_branch
        FOREIGN KEY (staff_id, branch_id) REFERENCES staff_members (id, branch_id);

-- Add branch keys to dependent clinical/dispensing transactions, backfilled from
-- their owning encounter or prescription, so cross-branch references are rejected.
ALTER TABLE lab_orders ADD COLUMN branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
UPDATE lab_orders lo JOIN encounters e ON e.id=lo.encounter_id SET lo.branch_id=e.branch_id;
ALTER TABLE lab_orders MODIFY branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
ALTER TABLE lab_orders
    ADD CONSTRAINT fk_lab_orders_encounter_branch
        FOREIGN KEY (encounter_id, branch_id) REFERENCES encounters (id, branch_id),
    ADD CONSTRAINT fk_lab_orders_doctor_branch
        FOREIGN KEY (ordered_by, branch_id) REFERENCES doctor_profiles (staff_id, branch_id);

ALTER TABLE prescriptions ADD COLUMN branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
UPDATE prescriptions p JOIN encounters e ON e.id=p.encounter_id SET p.branch_id=e.branch_id;
ALTER TABLE prescriptions MODIFY branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
ALTER TABLE prescriptions
    ADD CONSTRAINT uq_prescriptions_id_branch UNIQUE (id, branch_id),
    ADD CONSTRAINT fk_prescriptions_encounter_branch
        FOREIGN KEY (encounter_id, branch_id) REFERENCES encounters (id, branch_id),
    ADD CONSTRAINT fk_prescriptions_doctor_branch
        FOREIGN KEY (prescriber_staff_id, branch_id) REFERENCES doctor_profiles (staff_id, branch_id);

ALTER TABLE dispenses ADD COLUMN branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
UPDATE dispenses d JOIN prescriptions p ON p.id=d.prescription_id SET d.branch_id=p.branch_id;
ALTER TABLE dispenses MODIFY branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
ALTER TABLE dispenses
    ADD CONSTRAINT fk_dispenses_prescription_branch
        FOREIGN KEY (prescription_id, branch_id) REFERENCES prescriptions (id, branch_id),
    ADD CONSTRAINT fk_dispenses_location_branch
        FOREIGN KEY (inventory_location_id, branch_id) REFERENCES inventory_locations (id, branch_id),
    ADD CONSTRAINT fk_dispenses_staff_branch
        FOREIGN KEY (dispensed_by, branch_id) REFERENCES staff_members (id, branch_id);

ALTER TABLE medications
    ADD CONSTRAINT uq_medications_id_code UNIQUE (id, code);

ALTER TABLE medication_batches
    ADD CONSTRAINT uq_medication_batches_id_medication UNIQUE (id, medication_id);

ALTER TABLE staff_members
    ADD CONSTRAINT fk_staff_department_same_branch
    FOREIGN KEY (department_id, branch_id)
    REFERENCES departments (id, branch_id);

ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_department_same_branch
    FOREIGN KEY (department_id, branch_id)
    REFERENCES departments (id, branch_id),
    ADD CONSTRAINT fk_appointments_doctor_same_branch
    FOREIGN KEY (doctor_staff_id, branch_id)
    REFERENCES doctor_profiles (staff_id, branch_id);

ALTER TABLE encounters
    ADD CONSTRAINT fk_encounters_department_same_branch
    FOREIGN KEY (department_id, branch_id)
    REFERENCES departments (id, branch_id),
    ADD CONSTRAINT fk_encounters_doctor_same_branch
    FOREIGN KEY (attending_doctor_id, branch_id)
    REFERENCES doctor_profiles (staff_id, branch_id);

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoices_encounter_same_patient_branch
    FOREIGN KEY (encounter_id, branch_id, patient_id)
    REFERENCES encounters (id, branch_id, patient_id);

ALTER TABLE lab_results
    ADD CONSTRAINT ck_lab_result_release_requires_review
    CHECK (released_at IS NULL OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL));

-- -----------------------------------------------------------------------------
-- 1. Admissions and bed management
-- -----------------------------------------------------------------------------
CREATE TABLE wards (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    department_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ward_code           VARCHAR(24) NOT NULL,
    ward_name           VARCHAR(140) NOT NULL,
    ward_type           VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_ward_branch_code UNIQUE (branch_id, ward_code),
    CONSTRAINT uq_wards_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_wards_department_same_branch
        FOREIGN KEY (department_id, branch_id) REFERENCES departments (id, branch_id),
    CONSTRAINT ck_ward_type CHECK (ward_type IN ('GENERAL','ICU','MATERNITY','PEDIATRIC','SURGICAL','ISOLATION','EMERGENCY','OTHER')),
    CONSTRAINT ck_ward_name_nonblank CHECK (CHAR_LENGTH(TRIM(ward_name)) > 0),
    CONSTRAINT fk_wards_branch_id_1 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE rooms (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ward_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_number         VARCHAR(24) NOT NULL,
    room_type           VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    floor_label         VARCHAR(32),
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_room_ward_number UNIQUE (ward_id, room_number),
    CONSTRAINT uq_rooms_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_rooms_ward_same_branch
        FOREIGN KEY (ward_id, branch_id) REFERENCES wards (id, branch_id),
    CONSTRAINT ck_room_type CHECK (room_type IN ('STANDARD','PRIVATE','ISOLATION','ICU','PROCEDURE','OTHER')),
    CONSTRAINT ck_room_number_nonblank CHECK (CHAR_LENGTH(TRIM(room_number)) > 0)

) ENGINE=InnoDB;

CREATE TABLE beds (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bed_number          VARCHAR(24) NOT NULL,
    bed_type            VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    operational_status  VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_bed_room_number UNIQUE (room_id, bed_number),
    CONSTRAINT uq_beds_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_beds_room_same_branch
        FOREIGN KEY (room_id, branch_id) REFERENCES rooms (id, branch_id),
    CONSTRAINT ck_bed_type CHECK (bed_type IN ('STANDARD','ELECTRIC','ICU','PEDIATRIC','MATERNITY','OTHER')),
    CONSTRAINT ck_bed_operational_status CHECK (operational_status IN ('AVAILABLE','CLEANING','OUT_OF_SERVICE')),
    CONSTRAINT ck_bed_number_nonblank CHECK (CHAR_LENGTH(TRIM(bed_number)) > 0)

) ENGINE=InnoDB;

CREATE TABLE admissions (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admission_number    VARCHAR(40) NOT NULL,
    patient_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    attending_doctor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admission_type      VARCHAR(20) NOT NULL DEFAULT 'ELECTIVE',
    admission_status    VARCHAR(20) NOT NULL DEFAULT 'ADMITTED',
    admission_reason    VARCHAR(1000),
    admitted_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    admitted_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discharged_at      DATETIME(6),
    discharged_by      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    discharge_disposition VARCHAR(32),
    discharge_summary   TEXT,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_admission_branch_number UNIQUE (branch_id, admission_number),
    CONSTRAINT uq_admissions_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_admission_encounter_same_patient_branch
        FOREIGN KEY (encounter_id, branch_id, patient_id)
        REFERENCES encounters (id, branch_id, patient_id),
    CONSTRAINT fk_admission_doctor_same_branch
        FOREIGN KEY (attending_doctor_id, branch_id)
        REFERENCES doctor_profiles (staff_id, branch_id),
    CONSTRAINT ck_admission_type CHECK (admission_type IN ('ELECTIVE','EMERGENCY','TRANSFER','OBSERVATION')),
    CONSTRAINT ck_admission_status CHECK (admission_status IN ('ADMITTED','DISCHARGED','CANCELLED')),
    CONSTRAINT ck_admission_discharge_state CHECK (
        (admission_status = 'ADMITTED' AND discharged_at IS NULL AND discharged_by IS NULL)
        OR (admission_status = 'DISCHARGED' AND discharged_at IS NOT NULL AND discharged_by IS NOT NULL)
        OR (admission_status = 'CANCELLED' AND discharged_at IS NOT NULL)
    ),
    CONSTRAINT ck_admission_discharge_time CHECK (discharged_at IS NULL OR discharged_at >= admitted_at),
    CONSTRAINT ck_admission_disposition CHECK (discharge_disposition IS NULL OR discharge_disposition IN ('HOME','TRANSFERRED','AMA','DECEASED','OTHER')),
    CONSTRAINT fk_admissions_branch_id_2 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_patient_id_3 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_attending_doctor_id_4 FOREIGN KEY (attending_doctor_id) REFERENCES doctor_profiles (staff_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_admitted_by_5 FOREIGN KEY (admitted_by) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_discharged_by_6 FOREIGN KEY (discharged_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

-- uq_one_active_admission_per_patient_branch recreated with MySQL generated columns in 03_constraints.sql.

CREATE INDEX ix_admissions_patient_time ON admissions (patient_id, admitted_at DESC);
CREATE INDEX ix_admissions_status_time ON admissions (branch_id, admission_status, admitted_at DESC);

CREATE TABLE bed_assignments (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admission_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bed_id              CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assignment_status   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    started_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at            DATETIME(6),
    assigned_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    released_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    transfer_reason     VARCHAR(500),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bed_assignment_admission_branch
        FOREIGN KEY (admission_id, branch_id) REFERENCES admissions (id, branch_id),
    CONSTRAINT fk_bed_assignment_bed_branch
        FOREIGN KEY (bed_id, branch_id) REFERENCES beds (id, branch_id),
    CONSTRAINT ck_bed_assignment_status CHECK (assignment_status IN ('ACTIVE','COMPLETED','CANCELLED')),
    CONSTRAINT ck_bed_assignment_time CHECK (ended_at IS NULL OR ended_at > started_at),
    CONSTRAINT ck_bed_assignment_end_state CHECK (
        (assignment_status = 'ACTIVE' AND ended_at IS NULL AND released_by IS NULL)
        OR (assignment_status IN ('COMPLETED','CANCELLED') AND ended_at IS NOT NULL)
    ),
    CONSTRAINT fk_bed_assignments_assigned_by_7 FOREIGN KEY (assigned_by) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bed_assignments_released_by_8 FOREIGN KEY (released_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

-- uq_admission_one_current_bed recreated with MySQL generated columns in 03_constraints.sql.

CREATE INDEX ix_bed_assignments_admission_time ON bed_assignments (admission_id, started_at);

-- -----------------------------------------------------------------------------
-- 2. Suppliers, purchase orders and goods receipts (medication procurement v1)
-- -----------------------------------------------------------------------------
CREATE TABLE suppliers (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    supplier_code       VARCHAR(32) NOT NULL UNIQUE,
    supplier_name       VARCHAR(180) NOT NULL,
    contact_person      VARCHAR(140),
    phone_number        VARCHAR(32),
    email               VARCHAR(254),
    address_text        VARCHAR(500),
    tax_or_registration_no VARCHAR(80),
    supplier_status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_supplier_status CHECK (supplier_status IN ('ACTIVE','INACTIVE','BLOCKED')),
    CONSTRAINT ck_supplier_name_nonblank CHECK (CHAR_LENGTH(TRIM(supplier_name)) > 0)

) ENGINE=InnoDB;

CREATE TABLE purchase_orders (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    supplier_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_number        VARCHAR(40) NOT NULL,
    order_status        VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    subtotal            DECIMAL(14,2) NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_amount        DECIMAL(14,2) NOT NULL DEFAULT 0,
    requested_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approved_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    ordered_at          DATETIME(6),
    expected_delivery_on DATE,
    notes               VARCHAR(1000),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_purchase_order_branch_number UNIQUE (branch_id, order_number),
    CONSTRAINT uq_purchase_orders_id_branch UNIQUE (id, branch_id),
    CONSTRAINT ck_purchase_order_status CHECK (order_status IN ('DRAFT','SUBMITTED','APPROVED','ORDERED','PARTIALLY_RECEIVED','RECEIVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_purchase_order_amounts CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal
        AND tax_amount >= 0 AND total_amount >= 0
        AND total_amount = subtotal - discount_amount + tax_amount
    ),
    CONSTRAINT ck_purchase_order_approval CHECK (
        order_status NOT IN ('APPROVED','ORDERED','PARTIALLY_RECEIVED','RECEIVED') OR approved_by IS NOT NULL
    ),
    CONSTRAINT fk_purchase_orders_branch_id_9 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_supplier_id_10 FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_requested_by_11 FOREIGN KEY (requested_by) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_approved_by_12 FOREIGN KEY (approved_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE purchase_order_items (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    purchase_order_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_number         INTEGER NOT NULL,
    medication_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_description_snapshot VARCHAR(220) NOT NULL,
    ordered_quantity    DECIMAL(14,3) NOT NULL,
    unit_of_measure     VARCHAR(32) NOT NULL,
    unit_cost           DECIMAL(14,2) NOT NULL,
    line_discount       DECIMAL(14,2) NOT NULL DEFAULT 0,
    line_total          DECIMAL(14,2) NOT NULL,
    CONSTRAINT uq_purchase_order_line UNIQUE (purchase_order_id, line_number),
    CONSTRAINT uq_purchase_order_items_id_medication UNIQUE (id, medication_id),
    CONSTRAINT uq_purchase_order_items_id_order_med UNIQUE (id, purchase_order_id, medication_id),
    CONSTRAINT ck_po_item_line_number CHECK (line_number > 0),
    CONSTRAINT ck_po_item_amounts CHECK (
        ordered_quantity > 0 AND unit_cost >= 0 AND line_discount >= 0
        AND line_discount <= ordered_quantity * unit_cost AND line_total >= 0
    ),
    CONSTRAINT ck_po_item_total CHECK (line_total = round(ordered_quantity * unit_cost - line_discount, 2)),
    CONSTRAINT ck_po_item_description CHECK (CHAR_LENGTH(TRIM(item_description_snapshot)) > 0),
    CONSTRAINT fk_purchase_order_items_purchase_order_id_13 FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_items_medication_id_14 FOREIGN KEY (medication_id) REFERENCES medications (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE purchase_order_approvals (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    purchase_order_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_step       SMALLINT NOT NULL,
    approver_user_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    decision            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decision_note       VARCHAR(500),
    decided_at          DATETIME(6),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_po_approval_step UNIQUE (purchase_order_id, approval_step),
    CONSTRAINT ck_po_approval_step CHECK (approval_step > 0),
    CONSTRAINT ck_po_approval_decision CHECK (decision IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_po_approval_decision_actor CHECK (
        (decision = 'PENDING' AND decided_at IS NULL)
        OR (decision IN ('APPROVED','REJECTED') AND approver_user_id IS NOT NULL AND decided_at IS NOT NULL)
    ),
    CONSTRAINT fk_purchase_order_approvals_purchase_order_id_15 FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_approvals_approver_user_id_16 FOREIGN KEY (approver_user_id) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE goods_receipts (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purchase_order_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inventory_location_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_number      VARCHAR(40) NOT NULL,
    supplier_delivery_reference VARCHAR(100),
    receipt_status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    received_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    posted_by           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    received_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    posted_at           DATETIME(6),
    notes               VARCHAR(1000),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_goods_receipt_branch_number UNIQUE (branch_id, receipt_number),
    CONSTRAINT uq_goods_receipts_id_po UNIQUE (id, purchase_order_id),
    CONSTRAINT fk_goods_receipt_po_branch
        FOREIGN KEY (purchase_order_id, branch_id) REFERENCES purchase_orders (id, branch_id),
    CONSTRAINT fk_goods_receipt_location_branch
        FOREIGN KEY (inventory_location_id, branch_id) REFERENCES inventory_locations (id, branch_id),
    CONSTRAINT ck_goods_receipt_status CHECK (receipt_status IN ('DRAFT','POSTED','VOID')),
    CONSTRAINT ck_goods_receipt_posting CHECK (
        (receipt_status = 'POSTED' AND posted_by IS NOT NULL AND posted_at IS NOT NULL)
        OR (receipt_status IN ('DRAFT','VOID') AND posted_at IS NULL)
    ),
    CONSTRAINT fk_goods_receipts_received_by_17 FOREIGN KEY (received_by) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_goods_receipts_posted_by_18 FOREIGN KEY (posted_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE goods_receipt_items (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    receipt_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purchase_order_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purchase_order_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    medication_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    medication_batch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    accepted_quantity   DECIMAL(14,3) NOT NULL DEFAULT 0,
    rejected_quantity   DECIMAL(14,3) NOT NULL DEFAULT 0,
    rejection_reason    VARCHAR(300),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_receipt_item_receipt_po
        FOREIGN KEY (receipt_id, purchase_order_id) REFERENCES goods_receipts (id, purchase_order_id),
    CONSTRAINT fk_receipt_item_po_line_medication
        FOREIGN KEY (purchase_order_item_id, purchase_order_id, medication_id)
        REFERENCES purchase_order_items (id, purchase_order_id, medication_id),
    CONSTRAINT fk_receipt_item_batch_medication
        FOREIGN KEY (medication_batch_id, medication_id) REFERENCES medication_batches (id, medication_id),
    CONSTRAINT ck_receipt_item_quantities CHECK (
        accepted_quantity >= 0 AND rejected_quantity >= 0
        AND accepted_quantity + rejected_quantity > 0
    ),
    CONSTRAINT ck_receipt_item_rejection_reason CHECK (rejected_quantity = 0 OR CHAR_LENGTH(TRIM(coalesce(rejection_reason,''))) > 0)

) ENGINE=InnoDB;
CREATE INDEX ix_goods_receipt_items_po_line ON goods_receipt_items (purchase_order_item_id);

-- -----------------------------------------------------------------------------
-- 3. Smart operational analytics and decision support
-- -----------------------------------------------------------------------------
CREATE TABLE metric_definitions (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    metric_code         VARCHAR(64) NOT NULL UNIQUE,
    metric_name         VARCHAR(160) NOT NULL,
    source_module       VARCHAR(48) NOT NULL,
    description         VARCHAR(600) NOT NULL,
    unit                VARCHAR(32),
    aggregation_method  VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    refresh_interval_minutes INTEGER NOT NULL DEFAULT 60,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_metric_aggregation CHECK (aggregation_method IN ('COUNT','SUM','AVG','MIN','MAX','RATIO','CUSTOM')),
    CONSTRAINT ck_metric_refresh CHECK (refresh_interval_minutes > 0),
    CONSTRAINT ck_metric_name_nonblank CHECK (CHAR_LENGTH(TRIM(metric_name)) > 0)

) ENGINE=InnoDB;

CREATE TABLE metric_snapshots (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    metric_definition_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    department_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    period_granularity  VARCHAR(12) NOT NULL,
    window_start        DATETIME(6) NOT NULL,
    window_end          DATETIME(6) NOT NULL,
    metric_value        DECIMAL(20,6) NOT NULL,
    sample_count        BIGINT NOT NULL DEFAULT 0,
    dimensions          JSON NOT NULL DEFAULT (JSON_OBJECT()),
    computed_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_metric_snapshot_department_branch
        FOREIGN KEY (department_id, branch_id) REFERENCES departments (id, branch_id),
    CONSTRAINT ck_metric_period CHECK (period_granularity IN ('HOUR','DAY','WEEK','MONTH','QUARTER')),
    CONSTRAINT ck_metric_window CHECK (window_end > window_start),
    CONSTRAINT ck_metric_sample_count CHECK (sample_count >= 0),
    CONSTRAINT ck_metric_dimensions_object CHECK (JSON_TYPE(dimensions) = 'OBJECT'),
    CONSTRAINT fk_metric_snapshots_metric_definition_id_19 FOREIGN KEY (metric_definition_id) REFERENCES metric_definitions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_metric_snapshots_branch_id_20 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
-- uq_metric_snapshot_branch_period recreated with MySQL generated columns in 03_constraints.sql.

-- uq_metric_snapshot_department_period recreated with MySQL generated columns in 03_constraints.sql.

CREATE INDEX ix_metric_snapshots_lookup
    ON metric_snapshots (branch_id, metric_definition_id, window_start DESC);

CREATE TABLE decision_cards (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    card_code           VARCHAR(64) NOT NULL,
    deduplication_key   VARCHAR(160) NOT NULL,
    category            VARCHAR(32) NOT NULL,
    severity            VARCHAR(12) NOT NULL,
    card_status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    title               VARCHAR(200) NOT NULL,
    problem_statement   VARCHAR(1200) NOT NULL,
    analysis_summary    TEXT NOT NULL,
    recommendation      TEXT NOT NULL,
    evidence_window_start DATETIME(6) NOT NULL,
    evidence_window_end DATETIME(6) NOT NULL,
    observed_value      DECIMAL(20,6),
    threshold_value     DECIMAL(20,6),
    unit                VARCHAR(32),
    confidence_percent  DECIMAL(5,2),
    assumptions         JSON NOT NULL DEFAULT (JSON_ARRAY()),
    expected_impact     JSON NOT NULL DEFAULT (JSON_OBJECT()),
    generated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged_by     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    acknowledged_at     DATETIME(6),
    resolved_at         DATETIME(6),
    CONSTRAINT uq_decision_card_dedup UNIQUE (branch_id, deduplication_key),
    CONSTRAINT ck_decision_card_category CHECK (category IN ('WAIT_TIME','BED_OCCUPANCY','LAB_TAT','PHARMACY_STOCK','REVENUE','STAFFING','OTHER')),
    CONSTRAINT ck_decision_card_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT ck_decision_card_status CHECK (card_status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS','COMPLETED','DISMISSED','EXPIRED')),
    CONSTRAINT ck_decision_card_window CHECK (evidence_window_end > evidence_window_start),
    CONSTRAINT ck_decision_card_confidence CHECK (confidence_percent IS NULL OR confidence_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_decision_card_json CHECK (JSON_TYPE(assumptions) = 'ARRAY' AND JSON_TYPE(expected_impact) = 'OBJECT'),
    CONSTRAINT ck_decision_card_ack_pair CHECK (
        (acknowledged_by IS NULL) = (acknowledged_at IS NULL)
    ),
    CONSTRAINT fk_decision_cards_branch_id_21 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_decision_cards_acknowledged_by_22 FOREIGN KEY (acknowledged_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
CREATE INDEX ix_decision_cards_open_branch ON decision_cards (branch_id, severity, generated_at DESC);

CREATE TABLE decision_card_evidence (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    decision_card_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    metric_snapshot_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    evidence_code       VARCHAR(64) NOT NULL,
    evidence_description VARCHAR(600) NOT NULL,
    observed_value      DECIMAL(20,6),
    expected_value      DECIMAL(20,6),
    source_module       VARCHAR(48) NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_decision_card_evidence_decision_card_id_23 FOREIGN KEY (decision_card_id) REFERENCES decision_cards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_decision_card_evidence_metric_snapshot_id_24 FOREIGN KEY (metric_snapshot_id) REFERENCES metric_snapshots (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
CREATE INDEX ix_decision_evidence_card ON decision_card_evidence (decision_card_id);

CREATE TABLE decision_card_actions (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    decision_card_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_number       SMALLINT NOT NULL,
    action_description  VARCHAR(1200) NOT NULL,
    assigned_to         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    due_at              DATETIME(6),
    action_status       VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    outcome_summary     VARCHAR(1200),
    baseline_metric_snapshot_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    outcome_metric_snapshot_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    completed_at        DATETIME(6),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_decision_card_action_number UNIQUE (decision_card_id, action_number),
    CONSTRAINT ck_decision_action_number CHECK (action_number > 0),
    CONSTRAINT ck_decision_action_status CHECK (action_status IN ('PROPOSED','APPROVED','IN_PROGRESS','COMPLETED','REJECTED')),
    CONSTRAINT ck_decision_action_completion CHECK ((action_status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT fk_decision_card_actions_decision_card_id_25 FOREIGN KEY (decision_card_id) REFERENCES decision_cards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_decision_card_actions_assigned_to_26 FOREIGN KEY (assigned_to) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_decision_card_actions_baseline_metric_snapshot_id_27 FOREIGN KEY (baseline_metric_snapshot_id) REFERENCES metric_snapshots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_decision_card_actions_outcome_metric_snapshot_id_28 FOREIGN KEY (outcome_metric_snapshot_id) REFERENCES metric_snapshots (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 4. General ledger foundation (double-entry, single branch base currency v1)
-- -----------------------------------------------------------------------------
CREATE TABLE chart_of_accounts (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_code        VARCHAR(32) NOT NULL,
    account_name        VARCHAR(160) NOT NULL,
    account_type        VARCHAR(16) NOT NULL,
    normal_balance      VARCHAR(8) NOT NULL,
    parent_account_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    is_control_account  TINYINT(1) NOT NULL DEFAULT FALSE,
    allows_posting      TINYINT(1) NOT NULL DEFAULT TRUE,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_account_branch_code UNIQUE (branch_id, account_code),
    CONSTRAINT uq_chart_account_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_account_parent_same_branch
        FOREIGN KEY (parent_account_id, branch_id) REFERENCES chart_of_accounts (id, branch_id),
    CONSTRAINT ck_account_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_account_normal_balance CHECK (normal_balance IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_account_not_own_parent CHECK (parent_account_id IS NULL OR parent_account_id <> id),
    CONSTRAINT ck_account_name_nonblank CHECK (CHAR_LENGTH(TRIM(account_name)) > 0),
    CONSTRAINT fk_chart_of_accounts_branch_id_29 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE fiscal_periods (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    period_code         VARCHAR(24) NOT NULL,
    period_name         VARCHAR(120) NOT NULL,
    starts_on           DATE NOT NULL,
    ends_on             DATE NOT NULL,
    period_status       VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    closed_by           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    closed_at           DATETIME(6),
    CONSTRAINT uq_fiscal_period_branch_code UNIQUE (branch_id, period_code),
    CONSTRAINT uq_fiscal_period_id_branch UNIQUE (id, branch_id),
    CONSTRAINT ck_fiscal_period_dates CHECK (ends_on > starts_on),
    CONSTRAINT ck_fiscal_period_status CHECK (period_status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_fiscal_period_close CHECK (
        (period_status = 'OPEN' AND closed_by IS NULL AND closed_at IS NULL)
        OR (period_status = 'CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL)
    ),
    CONSTRAINT fk_fiscal_periods_branch_id_30 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fiscal_periods_closed_by_31 FOREIGN KEY (closed_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE journal_entries (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fiscal_period_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_number        VARCHAR(40) NOT NULL,
    entry_date          DATE NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    source_module       VARCHAR(48),
    source_reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    entry_status        VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    reversal_of_entry_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    posted_by           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    posted_at           DATETIME(6),
    CONSTRAINT uq_journal_branch_number UNIQUE (branch_id, entry_number),
    CONSTRAINT uq_journal_entry_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_journal_period_same_branch
        FOREIGN KEY (fiscal_period_id, branch_id) REFERENCES fiscal_periods (id, branch_id),
    CONSTRAINT fk_journal_reversal_same_branch
        FOREIGN KEY (reversal_of_entry_id, branch_id) REFERENCES journal_entries (id, branch_id),
    CONSTRAINT ck_journal_status CHECK (entry_status IN ('DRAFT','POSTED','VOID')),
    CONSTRAINT ck_journal_posting_metadata CHECK (
        (entry_status = 'DRAFT' AND posted_by IS NULL AND posted_at IS NULL)
        OR (entry_status = 'POSTED' AND posted_by IS NOT NULL AND posted_at IS NOT NULL)
        OR entry_status = 'VOID'
    ),
    CONSTRAINT ck_journal_not_self_reversal CHECK (reversal_of_entry_id IS NULL OR reversal_of_entry_id <> id),
    CONSTRAINT ck_journal_description_nonblank CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT fk_journal_entries_branch_id_32 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_journal_entries_created_by_33 FOREIGN KEY (created_by) REFERENCES user_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_journal_entries_posted_by_34 FOREIGN KEY (posted_by) REFERENCES user_accounts (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
-- uq_journal_source_once recreated with MySQL generated columns in 03_constraints.sql.

CREATE UNIQUE INDEX uq_journal_original_reversed_once
    ON journal_entries (reversal_of_entry_id);

CREATE TABLE journal_lines (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    journal_entry_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_number         SMALLINT NOT NULL,
    account_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    debit_amount        DECIMAL(16,2) NOT NULL DEFAULT 0,
    credit_amount       DECIMAL(16,2) NOT NULL DEFAULT 0,
    line_memo           VARCHAR(500),
    CONSTRAINT uq_journal_line_number UNIQUE (journal_entry_id, line_number),
    CONSTRAINT fk_journal_line_entry_branch
        FOREIGN KEY (journal_entry_id, branch_id) REFERENCES journal_entries (id, branch_id),
    CONSTRAINT fk_journal_line_account_branch
        FOREIGN KEY (account_id, branch_id) REFERENCES chart_of_accounts (id, branch_id),
    CONSTRAINT ck_journal_line_number CHECK (line_number > 0),
    CONSTRAINT ck_journal_line_one_side CHECK (
        (debit_amount > 0 AND credit_amount = 0)
        OR (credit_amount > 0 AND debit_amount = 0)
    )

) ENGINE=InnoDB;
CREATE INDEX ix_journal_lines_account ON journal_lines (account_id, journal_entry_id);

-- A journal can be posted only when it has at least two lines, balances exactly,
-- and belongs to an open fiscal period covering its entry date.

-- Posted journal headers and lines are immutable. Correct errors with a new
-- reversing entry and a replacement entry; never edit posted lines in place.

-- -----------------------------------------------------------------------------
-- 5. Supporting indexes for new domains
-- -----------------------------------------------------------------------------
CREATE INDEX ix_wards_branch_active ON wards (branch_id, is_active);
CREATE INDEX ix_rooms_ward_active ON rooms (ward_id, is_active);
CREATE INDEX ix_beds_room_status ON beds (room_id, operational_status);
CREATE INDEX ix_purchase_orders_supplier_date ON purchase_orders (supplier_id, created_at DESC);
CREATE INDEX ix_purchase_orders_branch_status ON purchase_orders (branch_id, order_status, created_at DESC);
CREATE INDEX ix_goods_receipts_po_date ON goods_receipts (purchase_order_id, received_at DESC);
CREATE INDEX ix_decision_actions_assignee_status ON decision_card_actions (assigned_to, action_status, due_at);
CREATE INDEX ix_journal_entries_branch_date ON journal_entries (branch_id, entry_date DESC);
CREATE INDEX ix_journal_entries_period_status ON journal_entries (fiscal_period_id, entry_status);

-- Operational notes:
-- 1. Inventory receipt posting must create/reuse medication_batches, insert positive
--    stock_movements and update stock_balances in one EJB-managed transaction.
-- 2. A posted goods receipt must have receipt items with medication_batch_id set;
--    validate this in the GoodsReceiptService before changing receipt_status to POSTED.
-- 3. Purchase-order cumulative received quantities are calculated from POSTED receipts;
--    do not maintain a client-editable received_quantity column.
-- 4. A single active admission is allowed per patient per branch. Transfers are modeled
--    as adjacent bed_assignments within one admission.
-- 5. Smart metrics are operational. Their formulas, windows and source queries must be
--    versioned/documented; do not use them for automated clinical diagnosis or treatment.
-- 6. Journal posting checks balancing and fiscal-period validity in the database. The
--    EJB service must additionally validate account posting permissions and source rules.
-- 7. The current procurement increment purchases medications. General medical
--    consumables need a unified inventory_item / stock_batch design before inclusion.
