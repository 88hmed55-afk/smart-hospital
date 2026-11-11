-- Smart Hospital | V002 | Admissions, procurement, smart operations, accounting
-- Target: PostgreSQL 16+
-- Apply only after V001__hospital_core.sql has completed successfully.
-- All operational and clinical test data must be synthetic.

BEGIN;
SET search_path TO hospital, public;

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
ALTER TABLE doctor_profiles ADD COLUMN branch_id UUID;
UPDATE doctor_profiles dp
   SET branch_id = sm.branch_id
  FROM staff_members sm
 WHERE sm.id = dp.staff_id;
ALTER TABLE doctor_profiles ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE doctor_profiles
    ADD CONSTRAINT uq_doctor_profiles_staff_branch UNIQUE (staff_id, branch_id),
    ADD CONSTRAINT fk_doctor_profiles_staff_branch
        FOREIGN KEY (staff_id, branch_id) REFERENCES staff_members (id, branch_id);

-- Add branch keys to dependent clinical/dispensing transactions, backfilled from
-- their owning encounter or prescription, so cross-branch references are rejected.
ALTER TABLE lab_orders ADD COLUMN branch_id UUID;
UPDATE lab_orders lo
   SET branch_id = e.branch_id
  FROM encounters e
 WHERE e.id = lo.encounter_id;
ALTER TABLE lab_orders ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE lab_orders
    ADD CONSTRAINT fk_lab_orders_encounter_branch
        FOREIGN KEY (encounter_id, branch_id) REFERENCES encounters (id, branch_id),
    ADD CONSTRAINT fk_lab_orders_doctor_branch
        FOREIGN KEY (ordered_by, branch_id) REFERENCES doctor_profiles (staff_id, branch_id);

ALTER TABLE prescriptions ADD COLUMN branch_id UUID;
UPDATE prescriptions p
   SET branch_id = e.branch_id
  FROM encounters e
 WHERE e.id = p.encounter_id;
ALTER TABLE prescriptions ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE prescriptions
    ADD CONSTRAINT uq_prescriptions_id_branch UNIQUE (id, branch_id),
    ADD CONSTRAINT fk_prescriptions_encounter_branch
        FOREIGN KEY (encounter_id, branch_id) REFERENCES encounters (id, branch_id),
    ADD CONSTRAINT fk_prescriptions_doctor_branch
        FOREIGN KEY (prescriber_staff_id, branch_id) REFERENCES doctor_profiles (staff_id, branch_id);

ALTER TABLE dispenses ADD COLUMN branch_id UUID;
UPDATE dispenses d
   SET branch_id = p.branch_id
  FROM prescriptions p
 WHERE p.id = d.prescription_id;
ALTER TABLE dispenses ALTER COLUMN branch_id SET NOT NULL;
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
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    department_id       UUID NOT NULL,
    ward_code           VARCHAR(24) NOT NULL,
    ward_name           VARCHAR(140) NOT NULL,
    ward_type           VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ward_branch_code UNIQUE (branch_id, ward_code),
    CONSTRAINT uq_wards_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_wards_department_same_branch
        FOREIGN KEY (department_id, branch_id) REFERENCES departments (id, branch_id),
    CONSTRAINT ck_ward_type CHECK (ward_type IN ('GENERAL','ICU','MATERNITY','PEDIATRIC','SURGICAL','ISOLATION','EMERGENCY','OTHER')),
    CONSTRAINT ck_ward_name_nonblank CHECK (length(btrim(ward_name)) > 0)
);

CREATE TABLE rooms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL,
    ward_id             UUID NOT NULL,
    room_number         VARCHAR(24) NOT NULL,
    room_type           VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    floor_label         VARCHAR(32),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_ward_number UNIQUE (ward_id, room_number),
    CONSTRAINT uq_rooms_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_rooms_ward_same_branch
        FOREIGN KEY (ward_id, branch_id) REFERENCES wards (id, branch_id),
    CONSTRAINT ck_room_type CHECK (room_type IN ('STANDARD','PRIVATE','ISOLATION','ICU','PROCEDURE','OTHER')),
    CONSTRAINT ck_room_number_nonblank CHECK (length(btrim(room_number)) > 0)
);

CREATE TABLE beds (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL,
    room_id             UUID NOT NULL,
    bed_number          VARCHAR(24) NOT NULL,
    bed_type            VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    operational_status  VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bed_room_number UNIQUE (room_id, bed_number),
    CONSTRAINT uq_beds_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_beds_room_same_branch
        FOREIGN KEY (room_id, branch_id) REFERENCES rooms (id, branch_id),
    CONSTRAINT ck_bed_type CHECK (bed_type IN ('STANDARD','ELECTRIC','ICU','PEDIATRIC','MATERNITY','OTHER')),
    CONSTRAINT ck_bed_operational_status CHECK (operational_status IN ('AVAILABLE','CLEANING','OUT_OF_SERVICE')),
    CONSTRAINT ck_bed_number_nonblank CHECK (length(btrim(bed_number)) > 0)
);

CREATE TABLE admissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    admission_number    VARCHAR(40) NOT NULL,
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    encounter_id        UUID NOT NULL UNIQUE,
    attending_doctor_id UUID NOT NULL REFERENCES doctor_profiles(staff_id) ON DELETE RESTRICT,
    admission_type      VARCHAR(20) NOT NULL DEFAULT 'ELECTIVE',
    admission_status    VARCHAR(20) NOT NULL DEFAULT 'ADMITTED',
    admission_reason    VARCHAR(1000),
    admitted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    admitted_by         UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    discharged_at      TIMESTAMPTZ,
    discharged_by      UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    discharge_disposition VARCHAR(32),
    discharge_summary   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    CONSTRAINT ck_admission_disposition CHECK (discharge_disposition IS NULL OR discharge_disposition IN ('HOME','TRANSFERRED','AMA','DECEASED','OTHER'))
);

CREATE UNIQUE INDEX uq_one_active_admission_per_patient_branch
    ON admissions (branch_id, patient_id)
    WHERE admission_status = 'ADMITTED';
CREATE INDEX ix_admissions_patient_time ON admissions (patient_id, admitted_at DESC);
CREATE INDEX ix_admissions_status_time ON admissions (branch_id, admission_status, admitted_at DESC);

CREATE TABLE bed_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL,
    admission_id        UUID NOT NULL,
    bed_id              UUID NOT NULL,
    assignment_status   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    assigned_by         UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    released_by         UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    transfer_reason     VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_bed_assignment_admission_branch
        FOREIGN KEY (admission_id, branch_id) REFERENCES admissions (id, branch_id),
    CONSTRAINT fk_bed_assignment_bed_branch
        FOREIGN KEY (bed_id, branch_id) REFERENCES beds (id, branch_id),
    CONSTRAINT ck_bed_assignment_status CHECK (assignment_status IN ('ACTIVE','COMPLETED','CANCELLED')),
    CONSTRAINT ck_bed_assignment_time CHECK (ended_at IS NULL OR ended_at > started_at),
    CONSTRAINT ck_bed_assignment_end_state CHECK (
        (assignment_status = 'ACTIVE' AND ended_at IS NULL AND released_by IS NULL)
        OR (assignment_status IN ('COMPLETED','CANCELLED') AND ended_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_admission_one_current_bed
    ON bed_assignments (admission_id)
    WHERE assignment_status = 'ACTIVE';
CREATE INDEX ix_bed_assignments_admission_time ON bed_assignments (admission_id, started_at);

ALTER TABLE bed_assignments ADD CONSTRAINT ex_bed_assignment_no_overlap
    EXCLUDE USING gist (
        bed_id WITH =,
        tstzrange(started_at, ended_at, '[)') WITH &&
    ) WHERE (assignment_status <> 'CANCELLED');

ALTER TABLE bed_assignments ADD CONSTRAINT ex_admission_one_bed_at_a_time
    EXCLUDE USING gist (
        admission_id WITH =,
        tstzrange(started_at, ended_at, '[)') WITH &&
    ) WHERE (assignment_status <> 'CANCELLED');

-- -----------------------------------------------------------------------------
-- 2. Suppliers, purchase orders and goods receipts (medication procurement v1)
-- -----------------------------------------------------------------------------
CREATE TABLE suppliers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code       VARCHAR(32) NOT NULL UNIQUE,
    supplier_name       VARCHAR(180) NOT NULL,
    contact_person      VARCHAR(140),
    phone_number        VARCHAR(32),
    email               VARCHAR(254),
    address_text        VARCHAR(500),
    tax_or_registration_no VARCHAR(80),
    supplier_status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_supplier_status CHECK (supplier_status IN ('ACTIVE','INACTIVE','BLOCKED')),
    CONSTRAINT ck_supplier_name_nonblank CHECK (length(btrim(supplier_name)) > 0)
);

CREATE TABLE purchase_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    supplier_id         UUID NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    order_number        VARCHAR(40) NOT NULL,
    order_status        VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    requested_by        UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    approved_by         UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    ordered_at          TIMESTAMPTZ,
    expected_delivery_on DATE,
    notes               VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    )
);

CREATE TABLE purchase_order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id   UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    line_number         INTEGER NOT NULL,
    medication_id       UUID NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    item_description_snapshot VARCHAR(220) NOT NULL,
    ordered_quantity    NUMERIC(14,3) NOT NULL,
    unit_of_measure     VARCHAR(32) NOT NULL,
    unit_cost           NUMERIC(14,2) NOT NULL,
    line_discount       NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(14,2) NOT NULL,
    CONSTRAINT uq_purchase_order_line UNIQUE (purchase_order_id, line_number),
    CONSTRAINT uq_purchase_order_items_id_medication UNIQUE (id, medication_id),
    CONSTRAINT uq_purchase_order_items_id_order_med UNIQUE (id, purchase_order_id, medication_id),
    CONSTRAINT ck_po_item_line_number CHECK (line_number > 0),
    CONSTRAINT ck_po_item_amounts CHECK (
        ordered_quantity > 0 AND unit_cost >= 0 AND line_discount >= 0
        AND line_discount <= ordered_quantity * unit_cost AND line_total >= 0
    ),
    CONSTRAINT ck_po_item_total CHECK (line_total = round(ordered_quantity * unit_cost - line_discount, 2)),
    CONSTRAINT ck_po_item_description CHECK (length(btrim(item_description_snapshot)) > 0)
);

CREATE TABLE purchase_order_approvals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id   UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    approval_step       SMALLINT NOT NULL,
    approver_user_id    UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    decision            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decision_note       VARCHAR(500),
    decided_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_po_approval_step UNIQUE (purchase_order_id, approval_step),
    CONSTRAINT ck_po_approval_step CHECK (approval_step > 0),
    CONSTRAINT ck_po_approval_decision CHECK (decision IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_po_approval_decision_actor CHECK (
        (decision = 'PENDING' AND decided_at IS NULL)
        OR (decision IN ('APPROVED','REJECTED') AND approver_user_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE TABLE goods_receipts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL,
    purchase_order_id   UUID NOT NULL,
    inventory_location_id UUID NOT NULL,
    receipt_number      VARCHAR(40) NOT NULL,
    supplier_delivery_reference VARCHAR(100),
    receipt_status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    received_by         UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    posted_by           UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_at           TIMESTAMPTZ,
    notes               VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    )
);

CREATE TABLE goods_receipt_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id          UUID NOT NULL,
    purchase_order_id   UUID NOT NULL,
    purchase_order_item_id UUID NOT NULL,
    medication_id       UUID NOT NULL,
    medication_batch_id UUID,
    accepted_quantity   NUMERIC(14,3) NOT NULL DEFAULT 0,
    rejected_quantity   NUMERIC(14,3) NOT NULL DEFAULT 0,
    rejection_reason    VARCHAR(300),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    CONSTRAINT ck_receipt_item_rejection_reason CHECK (rejected_quantity = 0 OR length(btrim(coalesce(rejection_reason,''))) > 0)
);
CREATE INDEX ix_goods_receipt_items_po_line ON goods_receipt_items (purchase_order_item_id);

-- -----------------------------------------------------------------------------
-- 3. Smart operational analytics and decision support
-- -----------------------------------------------------------------------------
CREATE TABLE metric_definitions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_code         VARCHAR(64) NOT NULL UNIQUE,
    metric_name         VARCHAR(160) NOT NULL,
    source_module       VARCHAR(48) NOT NULL,
    description         VARCHAR(600) NOT NULL,
    unit                VARCHAR(32),
    aggregation_method  VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    refresh_interval_minutes INTEGER NOT NULL DEFAULT 60,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_metric_aggregation CHECK (aggregation_method IN ('COUNT','SUM','AVG','MIN','MAX','RATIO','CUSTOM')),
    CONSTRAINT ck_metric_refresh CHECK (refresh_interval_minutes > 0),
    CONSTRAINT ck_metric_name_nonblank CHECK (length(btrim(metric_name)) > 0)
);

CREATE TABLE metric_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_definition_id UUID NOT NULL REFERENCES metric_definitions(id) ON DELETE RESTRICT,
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    department_id       UUID,
    period_granularity  VARCHAR(12) NOT NULL,
    window_start        TIMESTAMPTZ NOT NULL,
    window_end          TIMESTAMPTZ NOT NULL,
    metric_value        NUMERIC(20,6) NOT NULL,
    sample_count        BIGINT NOT NULL DEFAULT 0,
    dimensions          JSONB NOT NULL DEFAULT '{}'::jsonb,
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_metric_snapshot_department_branch
        FOREIGN KEY (department_id, branch_id) REFERENCES departments (id, branch_id),
    CONSTRAINT ck_metric_period CHECK (period_granularity IN ('HOUR','DAY','WEEK','MONTH','QUARTER')),
    CONSTRAINT ck_metric_window CHECK (window_end > window_start),
    CONSTRAINT ck_metric_sample_count CHECK (sample_count >= 0),
    CONSTRAINT ck_metric_dimensions_object CHECK (jsonb_typeof(dimensions) = 'object')
);
CREATE UNIQUE INDEX uq_metric_snapshot_branch_period
    ON metric_snapshots (metric_definition_id, branch_id, period_granularity, window_start, window_end)
    WHERE department_id IS NULL;
CREATE UNIQUE INDEX uq_metric_snapshot_department_period
    ON metric_snapshots (metric_definition_id, branch_id, department_id, period_granularity, window_start, window_end)
    WHERE department_id IS NOT NULL;
CREATE INDEX ix_metric_snapshots_lookup
    ON metric_snapshots (branch_id, metric_definition_id, window_start DESC);

CREATE TABLE decision_cards (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    card_code           VARCHAR(64) NOT NULL,
    deduplication_key   VARCHAR(160) NOT NULL,
    category            VARCHAR(32) NOT NULL,
    severity            VARCHAR(12) NOT NULL,
    card_status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    title               VARCHAR(200) NOT NULL,
    problem_statement   VARCHAR(1200) NOT NULL,
    analysis_summary    TEXT NOT NULL,
    recommendation      TEXT NOT NULL,
    evidence_window_start TIMESTAMPTZ NOT NULL,
    evidence_window_end TIMESTAMPTZ NOT NULL,
    observed_value      NUMERIC(20,6),
    threshold_value     NUMERIC(20,6),
    unit                VARCHAR(32),
    confidence_percent  NUMERIC(5,2),
    assumptions         JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_impact     JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_by     UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT uq_decision_card_dedup UNIQUE (branch_id, deduplication_key),
    CONSTRAINT ck_decision_card_category CHECK (category IN ('WAIT_TIME','BED_OCCUPANCY','LAB_TAT','PHARMACY_STOCK','REVENUE','STAFFING','OTHER')),
    CONSTRAINT ck_decision_card_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT ck_decision_card_status CHECK (card_status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS','COMPLETED','DISMISSED','EXPIRED')),
    CONSTRAINT ck_decision_card_window CHECK (evidence_window_end > evidence_window_start),
    CONSTRAINT ck_decision_card_confidence CHECK (confidence_percent IS NULL OR confidence_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_decision_card_json CHECK (jsonb_typeof(assumptions) = 'array' AND jsonb_typeof(expected_impact) = 'object'),
    CONSTRAINT ck_decision_card_ack_pair CHECK (
        (acknowledged_by IS NULL) = (acknowledged_at IS NULL)
    )
);
CREATE INDEX ix_decision_cards_open_branch ON decision_cards (branch_id, severity, generated_at DESC) WHERE card_status IN ('OPEN','ACKNOWLEDGED','IN_PROGRESS');

CREATE TABLE decision_card_evidence (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_card_id    UUID NOT NULL REFERENCES decision_cards(id) ON DELETE RESTRICT,
    metric_snapshot_id  UUID REFERENCES metric_snapshots(id) ON DELETE RESTRICT,
    evidence_code       VARCHAR(64) NOT NULL,
    evidence_description VARCHAR(600) NOT NULL,
    observed_value      NUMERIC(20,6),
    expected_value      NUMERIC(20,6),
    source_module       VARCHAR(48) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_decision_evidence_card ON decision_card_evidence (decision_card_id);

CREATE TABLE decision_card_actions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_card_id    UUID NOT NULL REFERENCES decision_cards(id) ON DELETE RESTRICT,
    action_number       SMALLINT NOT NULL,
    action_description  VARCHAR(1200) NOT NULL,
    assigned_to         UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    due_at              TIMESTAMPTZ,
    action_status       VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    outcome_summary     VARCHAR(1200),
    baseline_metric_snapshot_id UUID REFERENCES metric_snapshots(id) ON DELETE RESTRICT,
    outcome_metric_snapshot_id  UUID REFERENCES metric_snapshots(id) ON DELETE RESTRICT,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_decision_card_action_number UNIQUE (decision_card_id, action_number),
    CONSTRAINT ck_decision_action_number CHECK (action_number > 0),
    CONSTRAINT ck_decision_action_status CHECK (action_status IN ('PROPOSED','APPROVED','IN_PROGRESS','COMPLETED','REJECTED')),
    CONSTRAINT ck_decision_action_completion CHECK ((action_status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- -----------------------------------------------------------------------------
-- 4. General ledger foundation (double-entry, single branch base currency v1)
-- -----------------------------------------------------------------------------
CREATE TABLE chart_of_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    account_code        VARCHAR(32) NOT NULL,
    account_name        VARCHAR(160) NOT NULL,
    account_type        VARCHAR(16) NOT NULL,
    normal_balance      VARCHAR(8) NOT NULL,
    parent_account_id   UUID,
    is_control_account  BOOLEAN NOT NULL DEFAULT FALSE,
    allows_posting      BOOLEAN NOT NULL DEFAULT TRUE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_account_branch_code UNIQUE (branch_id, account_code),
    CONSTRAINT uq_chart_account_id_branch UNIQUE (id, branch_id),
    CONSTRAINT fk_account_parent_same_branch
        FOREIGN KEY (parent_account_id, branch_id) REFERENCES chart_of_accounts (id, branch_id),
    CONSTRAINT ck_account_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_account_normal_balance CHECK (normal_balance IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_account_not_own_parent CHECK (parent_account_id IS NULL OR parent_account_id <> id),
    CONSTRAINT ck_account_name_nonblank CHECK (length(btrim(account_name)) > 0)
);

CREATE TABLE fiscal_periods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    period_code         VARCHAR(24) NOT NULL,
    period_name         VARCHAR(120) NOT NULL,
    starts_on           DATE NOT NULL,
    ends_on             DATE NOT NULL,
    period_status       VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    closed_by           UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    closed_at           TIMESTAMPTZ,
    CONSTRAINT uq_fiscal_period_branch_code UNIQUE (branch_id, period_code),
    CONSTRAINT uq_fiscal_period_id_branch UNIQUE (id, branch_id),
    CONSTRAINT ck_fiscal_period_dates CHECK (ends_on > starts_on),
    CONSTRAINT ck_fiscal_period_status CHECK (period_status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_fiscal_period_close CHECK (
        (period_status = 'OPEN' AND closed_by IS NULL AND closed_at IS NULL)
        OR (period_status = 'CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL)
    )
);
ALTER TABLE fiscal_periods ADD CONSTRAINT ex_fiscal_period_no_overlap
    EXCLUDE USING gist (
        branch_id WITH =,
        daterange(starts_on, ends_on, '[)') WITH &&
    );

CREATE TABLE journal_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE RESTRICT,
    fiscal_period_id    UUID NOT NULL,
    entry_number        VARCHAR(40) NOT NULL,
    entry_date          DATE NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    source_module       VARCHAR(48),
    source_reference_id UUID,
    entry_status        VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    reversal_of_entry_id UUID,
    created_by          UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    posted_by           UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_at           TIMESTAMPTZ,
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
    CONSTRAINT ck_journal_description_nonblank CHECK (length(btrim(description)) > 0)
);
CREATE UNIQUE INDEX uq_journal_source_once
    ON journal_entries (branch_id, source_module, source_reference_id)
    WHERE source_module IS NOT NULL AND source_reference_id IS NOT NULL AND reversal_of_entry_id IS NULL;
CREATE UNIQUE INDEX uq_journal_original_reversed_once
    ON journal_entries (reversal_of_entry_id)
    WHERE reversal_of_entry_id IS NOT NULL;

CREATE TABLE journal_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id    UUID NOT NULL,
    branch_id           UUID NOT NULL,
    line_number         SMALLINT NOT NULL,
    account_id          UUID NOT NULL,
    debit_amount        NUMERIC(16,2) NOT NULL DEFAULT 0,
    credit_amount       NUMERIC(16,2) NOT NULL DEFAULT 0,
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
);
CREATE INDEX ix_journal_lines_account ON journal_lines (account_id, journal_entry_id);

-- A journal can be posted only when it has at least two lines, balances exactly,
-- and belongs to an open fiscal period covering its entry date.
CREATE OR REPLACE FUNCTION hospital.validate_journal_entry_before_post()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_line_count BIGINT;
    v_debit_total NUMERIC(20,2);
    v_credit_total NUMERIC(20,2);
    v_period_status VARCHAR(12);
    v_period_start DATE;
    v_period_end DATE;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.entry_status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Journal entries must be created in DRAFT status';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.entry_status = 'POSTED' AND OLD.entry_status = 'DRAFT' THEN
        SELECT count(*), coalesce(sum(debit_amount), 0), coalesce(sum(credit_amount), 0)
          INTO v_line_count, v_debit_total, v_credit_total
          FROM hospital.journal_lines
         WHERE journal_entry_id = NEW.id;

        IF v_line_count < 2 THEN
            RAISE EXCEPTION 'Journal entry % requires at least two lines', NEW.id;
        END IF;
        IF v_debit_total <= 0 OR v_debit_total <> v_credit_total THEN
            RAISE EXCEPTION 'Journal entry % is not balanced (debits %, credits %)',
                NEW.id, v_debit_total, v_credit_total;
        END IF;

        SELECT period_status, starts_on, ends_on
          INTO v_period_status, v_period_start, v_period_end
          FROM hospital.fiscal_periods
         WHERE id = NEW.fiscal_period_id AND branch_id = NEW.branch_id;

        IF v_period_status IS DISTINCT FROM 'OPEN'
           OR NEW.entry_date < v_period_start
           OR NEW.entry_date >= v_period_end THEN
            RAISE EXCEPTION 'Journal entry % does not belong to an open fiscal period', NEW.id;
        END IF;

        IF NEW.posted_by IS NULL OR NEW.posted_at IS NULL THEN
            RAISE EXCEPTION 'Posting user and timestamp are required';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_journal_before_post
BEFORE INSERT OR UPDATE OF entry_status ON journal_entries
FOR EACH ROW EXECUTE FUNCTION hospital.validate_journal_entry_before_post();

-- Posted journal headers and lines are immutable. Correct errors with a new
-- reversing entry and a replacement entry; never edit posted lines in place.
CREATE OR REPLACE FUNCTION hospital.prevent_posted_journal_line_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_status VARCHAR(12);
    v_new_status VARCHAR(12);
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.journal_entry_id <> NEW.journal_entry_id THEN
        RAISE EXCEPTION 'A journal line cannot be moved between entries';
    END IF;

    IF TG_OP IN ('UPDATE','DELETE') THEN
        SELECT entry_status INTO v_old_status
          FROM hospital.journal_entries
         WHERE id = OLD.journal_entry_id
         FOR UPDATE;
        IF v_old_status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'Journal lines can only be changed while the source entry is DRAFT';
        END IF;
    END IF;

    IF TG_OP IN ('INSERT','UPDATE') THEN
        SELECT entry_status INTO v_new_status
          FROM hospital.journal_entries
         WHERE id = NEW.journal_entry_id
         FOR UPDATE;
        IF v_new_status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'Journal lines can only be changed while the target entry is DRAFT';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_journal_lines_immutable_after_post
BEFORE INSERT OR UPDATE OR DELETE ON journal_lines
FOR EACH ROW EXECUTE FUNCTION hospital.prevent_posted_journal_line_mutation();

CREATE OR REPLACE FUNCTION hospital.prevent_posted_journal_header_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.entry_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Journal headers can only be changed while the entry is DRAFT';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_journal_header_immutable_after_post
BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW EXECUTE FUNCTION hospital.prevent_posted_journal_header_mutation();

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

COMMIT;

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
