-- Smart Hospital | V001 | Core schema baseline
-- Target: PostgreSQL 16+ (portable SQL where practical)
-- Scope: organizational core, identity/RBAC, patient journey, laboratory,
--        pharmacy stock, billing, notifications, transactional outbox, audit.
-- IMPORTANT: Development/test data must be synthetic. Never seed real patient data.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA IF NOT EXISTS hospital;
SET search_path TO hospital, public;

-- -----------------------------------------------------------------------------
-- 1. Organization and staff
-- -----------------------------------------------------------------------------
CREATE TABLE hospital_branches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(20) NOT NULL UNIQUE,
    name                VARCHAR(160) NOT NULL,
    timezone            VARCHAR(64) NOT NULL DEFAULT 'Asia/Aden',
    default_currency    CHAR(3) NOT NULL DEFAULT 'YER',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_branch_code_nonblank CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_branch_name_nonblank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE departments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id),
    code                VARCHAR(24) NOT NULL,
    name                VARCHAR(140) NOT NULL,
    department_type     VARCHAR(32) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_department_branch_code UNIQUE (branch_id, code),
    CONSTRAINT ck_department_type CHECK (department_type IN
        ('CLINIC','LABORATORY','PHARMACY','RADIOLOGY','INPATIENT','EMERGENCY','ADMINISTRATION','FINANCE','OTHER')),
    CONSTRAINT ck_department_name_nonblank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE staff_members (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id),
    department_id       UUID REFERENCES departments(id),
    employee_number     VARCHAR(32) NOT NULL,
    first_name          VARCHAR(80) NOT NULL,
    last_name           VARCHAR(80) NOT NULL,
    work_email          VARCHAR(254),
    phone_number        VARCHAR(32),
    job_title           VARCHAR(100),
    employment_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hired_on            DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_branch_employee UNIQUE (branch_id, employee_number),
    CONSTRAINT ck_staff_status CHECK (employment_status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')),
    CONSTRAINT ck_staff_names CHECK (length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0)
);

CREATE TABLE doctor_profiles (
    staff_id            UUID PRIMARY KEY REFERENCES staff_members(id),
    license_number      VARCHAR(80) NOT NULL UNIQUE,
    specialty           VARCHAR(120) NOT NULL,
    qualification       VARCHAR(160),
    can_order_labs      BOOLEAN NOT NULL DEFAULT TRUE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

-- -----------------------------------------------------------------------------
-- 2. Identity, roles, permissions and branch scope
-- -----------------------------------------------------------------------------
CREATE TABLE user_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id            UUID UNIQUE REFERENCES staff_members(id),
    username            VARCHAR(80) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    password_scheme     VARCHAR(40) NOT NULL DEFAULT 'ARGON2ID',
    account_status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at       TIMESTAMPTZ,
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_status CHECK (account_status IN ('ACTIVE','LOCKED','DISABLED')),
    CONSTRAINT ck_failed_login_nonnegative CHECK (failed_login_count >= 0),
    CONSTRAINT ck_password_hash_nonblank CHECK (length(btrim(password_hash)) > 0)
);
CREATE UNIQUE INDEX uq_user_username_ci ON user_accounts (lower(username));

CREATE TABLE roles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(48) NOT NULL UNIQUE,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(300),
    is_system_role      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(100) NOT NULL UNIQUE,
    module_code         VARCHAR(48) NOT NULL,
    description         VARCHAR(300) NOT NULL
);

CREATE TABLE user_roles (
    user_id             UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    role_id             UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by         UUID REFERENCES user_accounts(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id             UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id       UUID NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_branch_access (
    user_id             UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id) ON DELETE CASCADE,
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, branch_id)
);
CREATE UNIQUE INDEX uq_user_one_default_branch ON user_branch_access(user_id) WHERE is_default;

-- -----------------------------------------------------------------------------
-- 3. Patient master data and clinical record
-- -----------------------------------------------------------------------------
CREATE TABLE patients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrn                 VARCHAR(32) NOT NULL UNIQUE,
    first_name          VARCHAR(80) NOT NULL,
    middle_name         VARCHAR(80),
    last_name           VARCHAR(80) NOT NULL,
    date_of_birth       DATE,
    sex                 VARCHAR(16) NOT NULL,
    blood_group         VARCHAR(3),
    primary_phone       VARCHAR(32),
    email               VARCHAR(254),
    address_text        VARCHAR(500),
    preferred_language  VARCHAR(8) NOT NULL DEFAULT 'ar',
    patient_status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID REFERENCES user_accounts(id),
    CONSTRAINT ck_patient_names CHECK (length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0),
    CONSTRAINT ck_patient_sex CHECK (sex IN ('FEMALE','MALE','UNKNOWN','NOT_RECORDED')),
    CONSTRAINT ck_patient_status CHECK (patient_status IN ('ACTIVE','INACTIVE','DECEASED','MERGED')),
    CONSTRAINT ck_patient_blood_group CHECK (blood_group IS NULL OR blood_group IN ('A+','A-','B+','B-','AB+','AB-','O+','O-'))
);

CREATE TABLE patient_contacts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    contact_type        VARCHAR(24) NOT NULL,
    contact_name        VARCHAR(160),
    relationship        VARCHAR(60),
    phone_number        VARCHAR(32),
    email               VARCHAR(254),
    is_primary          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_patient_contact_type CHECK (contact_type IN ('PATIENT','EMERGENCY','GUARDIAN','OTHER')),
    CONSTRAINT ck_patient_contact_value CHECK (phone_number IS NOT NULL OR email IS NOT NULL)
);

CREATE TABLE patient_allergies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    allergen_name       VARCHAR(160) NOT NULL,
    reaction            VARCHAR(500),
    severity            VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    recorded_by         UUID REFERENCES staff_members(id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_allergy_severity CHECK (severity IN ('MILD','MODERATE','SEVERE','UNKNOWN')),
    CONSTRAINT ck_allergy_verification CHECK (verification_status IN ('UNVERIFIED','CONFIRMED','ENTERED_IN_ERROR'))
);

CREATE TABLE appointments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    doctor_staff_id     UUID NOT NULL REFERENCES doctor_profiles(staff_id) ON DELETE RESTRICT,
    department_id       UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    created_by          UUID REFERENCES user_accounts(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_appointment_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_appointment_status CHECK (status IN ('BOOKED','CONFIRMED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW'))
);
CREATE INDEX ix_appointments_patient_start ON appointments(patient_id, starts_at DESC);
CREATE INDEX ix_appointments_department_start ON appointments(department_id, starts_at);
CREATE INDEX ix_appointments_doctor_start ON appointments(doctor_staff_id, starts_at);
ALTER TABLE appointments ADD CONSTRAINT ex_doctor_appointment_overlap
    EXCLUDE USING gist (
        doctor_staff_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('BOOKED','CONFIRMED','CHECKED_IN'));

CREATE TABLE encounters (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    appointment_id      UUID UNIQUE REFERENCES appointments(id) ON DELETE RESTRICT,
    department_id       UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    attending_doctor_id UUID NOT NULL REFERENCES doctor_profiles(staff_id) ON DELETE RESTRICT,
    encounter_type      VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT',
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    chief_complaint     VARCHAR(1000),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    created_by          UUID REFERENCES user_accounts(id),
    CONSTRAINT ck_encounter_type CHECK (encounter_type IN ('OUTPATIENT','EMERGENCY','INPATIENT','FOLLOW_UP')),
    CONSTRAINT ck_encounter_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_encounter_time CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX ix_encounters_patient_started ON encounters(patient_id, started_at DESC);

CREATE TABLE vital_signs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounters(id) ON DELETE RESTRICT,
    measured_by         UUID NOT NULL REFERENCES staff_members(id),
    measured_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    temperature_c      NUMERIC(4,1),
    systolic_mmhg       SMALLINT,
    diastolic_mmhg      SMALLINT,
    pulse_bpm           SMALLINT,
    respiratory_rate    SMALLINT,
    oxygen_saturation   NUMERIC(5,2),
    weight_kg           NUMERIC(6,2),
    height_cm           NUMERIC(6,2),
    notes               VARCHAR(500),
    CONSTRAINT ck_vitals_temperature CHECK (temperature_c IS NULL OR temperature_c BETWEEN 25 AND 45),
    CONSTRAINT ck_vitals_bp CHECK ((systolic_mmhg IS NULL OR systolic_mmhg BETWEEN 20 AND 300)
                               AND (diastolic_mmhg IS NULL OR diastolic_mmhg BETWEEN 10 AND 200)),
    CONSTRAINT ck_vitals_pulse CHECK (pulse_bpm IS NULL OR pulse_bpm BETWEEN 1 AND 300),
    CONSTRAINT ck_vitals_resp CHECK (respiratory_rate IS NULL OR respiratory_rate BETWEEN 1 AND 100),
    CONSTRAINT ck_vitals_spo2 CHECK (oxygen_saturation IS NULL OR oxygen_saturation BETWEEN 0 AND 100),
    CONSTRAINT ck_vitals_weight CHECK (weight_kg IS NULL OR weight_kg > 0),
    CONSTRAINT ck_vitals_height CHECK (height_cm IS NULL OR height_cm > 0)
);

CREATE TABLE clinical_notes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounters(id) ON DELETE RESTRICT,
    author_staff_id     UUID NOT NULL REFERENCES staff_members(id),
    note_type           VARCHAR(24) NOT NULL,
    note_text           TEXT NOT NULL,
    note_status         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    signed_at           TIMESTAMPTZ,
    amended_from_id     UUID REFERENCES clinical_notes(id),
    CONSTRAINT ck_clinical_note_type CHECK (note_type IN ('HISTORY','EXAMINATION','ASSESSMENT','PLAN','PROGRESS','DISCHARGE','OTHER')),
    CONSTRAINT ck_clinical_note_status CHECK (note_status IN ('DRAFT','SIGNED','AMENDED')),
    CONSTRAINT ck_signed_note_time CHECK (note_status = 'DRAFT' OR signed_at IS NOT NULL),
    CONSTRAINT ck_clinical_note_nonblank CHECK (length(btrim(note_text)) > 0)
);
CREATE INDEX ix_clinical_notes_encounter ON clinical_notes(encounter_id, created_at);

CREATE TABLE diagnosis_catalog (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_system         VARCHAR(32) NOT NULL,
    diagnosis_code      VARCHAR(32) NOT NULL,
    description         VARCHAR(300) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_diagnosis_code UNIQUE (code_system, diagnosis_code)
);

CREATE TABLE encounter_diagnoses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounters(id) ON DELETE RESTRICT,
    diagnosis_id        UUID NOT NULL REFERENCES diagnosis_catalog(id) ON DELETE RESTRICT,
    recorded_by         UUID NOT NULL REFERENCES staff_members(id),
    is_primary          BOOLEAN NOT NULL DEFAULT FALSE,
    clinical_status     VARCHAR(20) NOT NULL DEFAULT 'PROVISIONAL',
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_encounter_diagnosis UNIQUE (encounter_id, diagnosis_id),
    CONSTRAINT ck_diagnosis_status CHECK (clinical_status IN ('PROVISIONAL','CONFIRMED','RULED_OUT'))
);
CREATE UNIQUE INDEX uq_encounter_one_primary_diagnosis ON encounter_diagnoses(encounter_id) WHERE is_primary;

-- -----------------------------------------------------------------------------
-- 4. Laboratory
-- -----------------------------------------------------------------------------
CREATE TABLE lab_test_catalog (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(32) NOT NULL UNIQUE,
    name                VARCHAR(160) NOT NULL,
    specimen_type       VARCHAR(80),
    result_unit         VARCHAR(40),
    reference_range     VARCHAR(120),
    standard_price      NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    expected_minutes    INTEGER,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_lab_test_price CHECK (standard_price >= 0),
    CONSTRAINT ck_lab_test_duration CHECK (expected_minutes IS NULL OR expected_minutes > 0)
);

CREATE TABLE lab_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounters(id) ON DELETE RESTRICT,
    ordered_by          UUID NOT NULL REFERENCES doctor_profiles(staff_id),
    priority            VARCHAR(12) NOT NULL DEFAULT 'ROUTINE',
    status              VARCHAR(20) NOT NULL DEFAULT 'ORDERED',
    clinical_question   VARCHAR(500),
    ordered_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_lab_order_priority CHECK (priority IN ('ROUTINE','URGENT','STAT')),
    CONSTRAINT ck_lab_order_status CHECK (status IN ('ORDERED','ACCEPTED','IN_PROGRESS','PARTIALLY_COMPLETED','COMPLETED','CANCELLED'))
);

CREATE TABLE lab_order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id        UUID NOT NULL REFERENCES lab_orders(id) ON DELETE RESTRICT,
    lab_test_id         UUID NOT NULL REFERENCES lab_test_catalog(id) ON DELETE RESTRICT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    specimen_code       VARCHAR(48),
    collected_at        TIMESTAMPTZ,
    collected_by        UUID REFERENCES staff_members(id),
    CONSTRAINT ck_lab_item_status CHECK (status IN ('PENDING','COLLECTED','PROCESSING','RESULTED','REVIEWED','CANCELLED'))
);

CREATE TABLE lab_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_item_id   UUID NOT NULL UNIQUE REFERENCES lab_order_items(id) ON DELETE RESTRICT,
    result_text         TEXT,
    result_numeric      NUMERIC(16,5),
    result_unit         VARCHAR(40),
    reference_range     VARCHAR(120),
    abnormal_flag       VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',
    performed_by        UUID REFERENCES staff_members(id),
    reviewed_by         UUID REFERENCES staff_members(id),
    performed_at        TIMESTAMPTZ,
    reviewed_at         TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    CONSTRAINT ck_lab_result_value CHECK (result_text IS NOT NULL OR result_numeric IS NOT NULL),
    CONSTRAINT ck_lab_abnormal_flag CHECK (abnormal_flag IN ('LOW','NORMAL','HIGH','CRITICAL','UNKNOWN')),
    CONSTRAINT ck_lab_review_pair CHECK ((reviewed_by IS NULL AND reviewed_at IS NULL) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

-- -----------------------------------------------------------------------------
-- 5. Pharmacy and inventory ledger
-- -----------------------------------------------------------------------------
CREATE TABLE medications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(40) NOT NULL UNIQUE,
    generic_name        VARCHAR(160) NOT NULL,
    brand_name          VARCHAR(160),
    strength            VARCHAR(80),
    dosage_form         VARCHAR(60),
    route               VARCHAR(60),
    dispensing_unit     VARCHAR(40) NOT NULL DEFAULT 'unit',
    is_controlled      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory_locations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id),
    code                VARCHAR(32) NOT NULL,
    name                VARCHAR(140) NOT NULL,
    location_type       VARCHAR(20) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_inventory_location_code UNIQUE (branch_id, code),
    CONSTRAINT ck_inventory_location_type CHECK (location_type IN ('MAIN_STORE','PHARMACY','WARD','LAB','OTHER'))
);

CREATE TABLE medication_batches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id       UUID NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    batch_number        VARCHAR(80) NOT NULL,
    manufacturer       VARCHAR(160),
    manufactured_on     DATE,
    expires_on          DATE NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_medication_batch UNIQUE (medication_id, batch_number),
    CONSTRAINT ck_batch_dates CHECK (manufactured_on IS NULL OR expires_on >= manufactured_on)
);

CREATE TABLE stock_balances (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id         UUID NOT NULL REFERENCES inventory_locations(id) ON DELETE RESTRICT,
    batch_id            UUID NOT NULL REFERENCES medication_batches(id) ON DELETE RESTRICT,
    quantity_on_hand    NUMERIC(14,3) NOT NULL DEFAULT 0,
    quantity_reserved   NUMERIC(14,3) NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_location_batch UNIQUE (location_id, batch_id),
    CONSTRAINT ck_stock_quantities CHECK (quantity_on_hand >= 0 AND quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)
);

CREATE TABLE stock_movements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id         UUID NOT NULL REFERENCES inventory_locations(id) ON DELETE RESTRICT,
    batch_id            UUID NOT NULL REFERENCES medication_batches(id) ON DELETE RESTRICT,
    movement_type       VARCHAR(20) NOT NULL,
    quantity_delta      NUMERIC(14,3) NOT NULL,
    reference_type      VARCHAR(40),
    reference_id        UUID,
    transfer_group_id   UUID,
    reason              VARCHAR(300),
    performed_by        UUID REFERENCES user_accounts(id),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('RECEIPT','DISPENSE','TRANSFER_IN','TRANSFER_OUT','ADJUSTMENT','RETURN','EXPIRED','DAMAGE')),
    CONSTRAINT ck_stock_delta_nonzero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_movement_direction CHECK (
        (movement_type IN ('RECEIPT','TRANSFER_IN','RETURN') AND quantity_delta > 0)
        OR (movement_type IN ('DISPENSE','TRANSFER_OUT','EXPIRED','DAMAGE') AND quantity_delta < 0)
        OR movement_type = 'ADJUSTMENT'
    )
);
CREATE INDEX ix_stock_movements_batch_time ON stock_movements(batch_id, occurred_at DESC);

CREATE TABLE prescriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounters(id) ON DELETE RESTRICT,
    prescriber_staff_id UUID NOT NULL REFERENCES doctor_profiles(staff_id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ordered_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        UUID REFERENCES user_accounts(id),
    CONSTRAINT ck_prescription_status CHECK (status IN ('ACTIVE','PARTIALLY_DISPENSED','DISPENSED','CANCELLED'))
);

CREATE TABLE prescription_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id     UUID NOT NULL REFERENCES prescriptions(id) ON DELETE RESTRICT,
    medication_id       UUID NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    dose                VARCHAR(80) NOT NULL,
    route               VARCHAR(60),
    frequency           VARCHAR(100) NOT NULL,
    duration_days       INTEGER,
    quantity_prescribed NUMERIC(14,3) NOT NULL,
    instructions        VARCHAR(500),
    CONSTRAINT ck_prescription_quantity CHECK (quantity_prescribed > 0),
    CONSTRAINT ck_prescription_duration CHECK (duration_days IS NULL OR duration_days > 0)
);

CREATE TABLE dispenses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id     UUID NOT NULL REFERENCES prescriptions(id) ON DELETE RESTRICT,
    dispensed_by        UUID NOT NULL REFERENCES staff_members(id),
    inventory_location_id UUID NOT NULL REFERENCES inventory_locations(id),
    status              VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    dispensed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dispense_status CHECK (status IN ('DRAFT','POSTED','VOID'))
);

CREATE TABLE dispense_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_id         UUID NOT NULL REFERENCES dispenses(id) ON DELETE RESTRICT,
    prescription_item_id UUID NOT NULL REFERENCES prescription_items(id) ON DELETE RESTRICT,
    batch_id            UUID NOT NULL REFERENCES medication_batches(id) ON DELETE RESTRICT,
    quantity_dispensed  NUMERIC(14,3) NOT NULL,
    CONSTRAINT uq_dispense_item_batch UNIQUE (dispense_id, prescription_item_id, batch_id),
    CONSTRAINT ck_dispense_item_quantity CHECK (quantity_dispensed > 0)
);

-- -----------------------------------------------------------------------------
-- 6. Service catalogue, invoices and payments
-- -----------------------------------------------------------------------------
CREATE TABLE service_catalog (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(40) NOT NULL UNIQUE,
    name                VARCHAR(160) NOT NULL,
    service_type        VARCHAR(24) NOT NULL,
    standard_price      NUMERIC(14,2) NOT NULL,
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_service_type CHECK (service_type IN ('CONSULTATION','LABORATORY','RADIOLOGY','PROCEDURE','ROOM','OTHER')),
    CONSTRAINT ck_service_price CHECK (standard_price >= 0)
);

CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES hospital_branches(id),
    invoice_number      VARCHAR(40) NOT NULL,
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    encounter_id        UUID REFERENCES encounters(id) ON DELETE RESTRICT,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    issued_at           TIMESTAMPTZ,
    created_by          UUID REFERENCES user_accounts(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_branch_number UNIQUE (branch_id, invoice_number),
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOID')),
    CONSTRAINT ck_invoice_amounts CHECK (subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_invoice_total CHECK (total_amount = subtotal - discount_amount + tax_amount)
);

CREATE TABLE invoice_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id          UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    service_id          UUID REFERENCES service_catalog(id) ON DELETE RESTRICT,
    dispense_item_id    UUID REFERENCES dispense_items(id) ON DELETE RESTRICT,
    description_snapshot VARCHAR(240) NOT NULL,
    quantity            NUMERIC(14,3) NOT NULL,
    unit_price          NUMERIC(14,2) NOT NULL,
    line_discount       NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_invoice_item_source CHECK ((service_id IS NOT NULL) <> (dispense_item_id IS NOT NULL)),
    CONSTRAINT ck_invoice_item_amounts CHECK (quantity > 0 AND unit_price >= 0 AND line_discount >= 0 AND line_total >= 0)
);
CREATE UNIQUE INDEX uq_dispense_item_invoiced_once ON invoice_items(dispense_item_id) WHERE dispense_item_id IS NOT NULL;

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id          UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    receipt_number      VARCHAR(40) NOT NULL UNIQUE,
    amount              NUMERIC(14,2) NOT NULL,
    currency_code       CHAR(3) NOT NULL,
    payment_method      VARCHAR(20) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    transaction_reference VARCHAR(120),
    received_by         UUID NOT NULL REFERENCES user_accounts(id),
    paid_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','INSURANCE','OTHER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('POSTED','VOID','REFUNDED'))
);
CREATE INDEX ix_payments_invoice_time ON payments(invoice_id, paid_at DESC);

-- -----------------------------------------------------------------------------
-- 7. Messaging, notifications, audit and smart operational alerts
-- -----------------------------------------------------------------------------
CREATE TABLE message_outbox (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type          VARCHAR(120) NOT NULL,
    aggregate_type      VARCHAR(80) NOT NULL,
    aggregate_id        UUID NOT NULL,
    idempotency_key     VARCHAR(160) NOT NULL UNIQUE,
    payload             JSONB NOT NULL,
    delivery_status     VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    last_error_code     VARCHAR(80),
    CONSTRAINT ck_outbox_status CHECK (delivery_status IN ('PENDING','PROCESSING','PUBLISHED','DEAD_LETTER')),
    CONSTRAINT ck_outbox_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX ix_outbox_pending ON message_outbox(next_attempt_at, created_at) WHERE delivery_status IN ('PENDING','PROCESSING');

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id   UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    notification_type   VARCHAR(40) NOT NULL,
    title               VARCHAR(180) NOT NULL,
    body                VARCHAR(1000) NOT NULL,
    reference_type      VARCHAR(60),
    reference_id        UUID,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user_unread ON notifications(recipient_user_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id       UUID REFERENCES user_accounts(id),
    branch_id           UUID REFERENCES hospital_branches(id),
    action_code         VARCHAR(80) NOT NULL,
    resource_type       VARCHAR(80) NOT NULL,
    resource_id         UUID,
    outcome             VARCHAR(16) NOT NULL,
    ip_address          INET,
    user_agent          VARCHAR(400),
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILURE'))
);
CREATE INDEX ix_audit_resource_time ON audit_logs(resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_actor_time ON audit_logs(actor_user_id, occurred_at DESC);

CREATE TABLE operational_alerts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID REFERENCES hospital_branches(id),
    alert_code          VARCHAR(80) NOT NULL,
    source_module       VARCHAR(48) NOT NULL,
    severity            VARCHAR(12) NOT NULL,
    title               VARCHAR(180) NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    metric_value        NUMERIC(18,4),
    threshold_value     NUMERIC(18,4),
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_by     UUID REFERENCES user_accounts(id),
    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT ck_alert_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED','DISMISSED'))
);
CREATE INDEX ix_alerts_open_severity ON operational_alerts(severity, generated_at DESC) WHERE status = 'OPEN';

CREATE TABLE system_settings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key         VARCHAR(100) NOT NULL,
    branch_id           UUID REFERENCES hospital_branches(id),
    setting_value       JSONB NOT NULL,
    description         VARCHAR(300),
    updated_by          UUID REFERENCES user_accounts(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_system_setting_global ON system_settings(setting_key) WHERE branch_id IS NULL;
CREATE UNIQUE INDEX uq_system_setting_branch ON system_settings(setting_key, branch_id) WHERE branch_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 8. Essential supporting indexes
-- -----------------------------------------------------------------------------
CREATE INDEX ix_patients_name ON patients(last_name, first_name);
CREATE INDEX ix_patients_phone ON patients(primary_phone) WHERE primary_phone IS NOT NULL;
CREATE INDEX ix_lab_orders_encounter_date ON lab_orders(encounter_id, ordered_at DESC);
CREATE INDEX ix_lab_items_status ON lab_order_items(status);
CREATE INDEX ix_prescriptions_encounter ON prescriptions(encounter_id, ordered_at DESC);
CREATE INDEX ix_batches_expiry ON medication_batches(expires_on);
CREATE INDEX ix_stock_balances_location ON stock_balances(location_id);
CREATE INDEX ix_invoices_patient_date ON invoices(patient_id, created_at DESC);

COMMIT;

-- Implementation notes:
-- 1. Update updated_at in application services or add controlled DB triggers.
-- 2. Never delete signed clinical notes, posted stock movements, posted payments,
--    or audit logs. Use correction/void workflows with new audit events.
-- 3. Keep invoice totals and stock balances transactionally consistent with their
--    detail/ledger rows. Do not update these values from the React client.
-- 4. Appointment exclusion constraint requires PostgreSQL btree_gist extension.
-- 5. Add admission/bed, procurement, insurance, imaging, and accounting-ledger
--    tables in later migrations after their workflows are specified.
