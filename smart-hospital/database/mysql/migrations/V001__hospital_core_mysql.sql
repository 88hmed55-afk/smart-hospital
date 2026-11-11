-- Smart Hospital | V001 | Core schema baseline
-- Target: MySQL 8.0.38
-- Scope: organizational core, identity/RBAC, patient journey, laboratory,
--        pharmacy stock, billing, notifications, transactional outbox, audit.
-- IMPORTANT: Development/test data must be synthetic. Never seed real patient data.
CREATE DATABASE IF NOT EXISTS hospital CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE hospital;
USE hospital;
-- -----------------------------------------------------------------------------
-- 1. Organization and staff
-- -----------------------------------------------------------------------------
CREATE TABLE hospital_branches (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code                VARCHAR(20) NOT NULL UNIQUE,
    name                VARCHAR(160) NOT NULL,
    timezone            VARCHAR(64) NOT NULL DEFAULT 'Asia/Aden',
    default_currency    CHAR(3) NOT NULL DEFAULT 'YER',
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_branch_code_nonblank CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_branch_name_nonblank CHECK (CHAR_LENGTH(TRIM(name)) > 0)

) ENGINE=InnoDB;

CREATE TABLE departments (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code                VARCHAR(24) NOT NULL,
    name                VARCHAR(140) NOT NULL,
    department_type     VARCHAR(32) NOT NULL,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_department_branch_code UNIQUE (branch_id, code),
    CONSTRAINT ck_department_type CHECK (department_type IN
        ('CLINIC','LABORATORY','PHARMACY','RADIOLOGY','INPATIENT','EMERGENCY','ADMINISTRATION','FINANCE','OTHER')),
    CONSTRAINT ck_department_name_nonblank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT fk_departments_branch_id_1 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id)

) ENGINE=InnoDB;

CREATE TABLE staff_members (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    department_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    employee_number     VARCHAR(32) NOT NULL,
    first_name          VARCHAR(80) NOT NULL,
    last_name           VARCHAR(80) NOT NULL,
    work_email          VARCHAR(254),
    phone_number        VARCHAR(32),
    job_title           VARCHAR(100),
    employment_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hired_on            DATE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_staff_branch_employee UNIQUE (branch_id, employee_number),
    CONSTRAINT ck_staff_status CHECK (employment_status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')),
    CONSTRAINT ck_staff_names CHECK (CHAR_LENGTH(TRIM(first_name)) > 0 AND CHAR_LENGTH(TRIM(last_name)) > 0),
    CONSTRAINT fk_staff_members_branch_id_2 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id),
    CONSTRAINT fk_staff_members_department_id_3 FOREIGN KEY (department_id) REFERENCES departments (id)

) ENGINE=InnoDB;

CREATE TABLE doctor_profiles (

    staff_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    license_number      VARCHAR(80) NOT NULL UNIQUE,
    specialty           VARCHAR(120) NOT NULL,
    qualification       VARCHAR(160),
    can_order_labs      TINYINT(1) NOT NULL DEFAULT TRUE,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_doctor_profiles_staff_id_4 FOREIGN KEY (staff_id) REFERENCES staff_members (id)

) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 2. Identity, roles, permissions and branch scope
-- -----------------------------------------------------------------------------
CREATE TABLE user_accounts (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    staff_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    username            VARCHAR(80) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    password_scheme     VARCHAR(40) NOT NULL DEFAULT 'ARGON2ID',
    account_status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password TINYINT(1) NOT NULL DEFAULT TRUE,
    last_login_at       DATETIME(6),
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    locked_until        DATETIME(6),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_user_status CHECK (account_status IN ('ACTIVE','LOCKED','DISABLED')),
    CONSTRAINT ck_failed_login_nonnegative CHECK (failed_login_count >= 0),
    CONSTRAINT ck_password_hash_nonblank CHECK (CHAR_LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT fk_user_accounts_staff_id_5 FOREIGN KEY (staff_id) REFERENCES staff_members (id)

) ENGINE=InnoDB;
CREATE UNIQUE INDEX uq_user_username_ci ON user_accounts (username);

CREATE TABLE roles (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code                VARCHAR(48) NOT NULL UNIQUE,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(300),
    is_system_role      TINYINT(1) NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)

) ENGINE=InnoDB;

CREATE TABLE permissions (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code                VARCHAR(100) NOT NULL UNIQUE,
    module_code         VARCHAR(48) NOT NULL,
    description         VARCHAR(300) NOT NULL

) ENGINE=InnoDB;

CREATE TABLE user_roles (

    user_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assigned_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assigned_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id_6 FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role_id_7 FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_assigned_by_8 FOREIGN KEY (assigned_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;

CREATE TABLE role_permissions (

    role_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permission_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role_id_9 FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission_id_10 FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE user_branch_access (

    user_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_default          TINYINT(1) NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branch_access_user_id_11 FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_branch_access_branch_id_12 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE CASCADE

) ENGINE=InnoDB;
-- uq_user_one_default_branch recreated with MySQL generated columns in 03_constraints.sql.


-- -----------------------------------------------------------------------------
-- 3. Patient master data and clinical record
-- -----------------------------------------------------------------------------
CREATE TABLE patients (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
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
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT ck_patient_names CHECK (CHAR_LENGTH(TRIM(first_name)) > 0 AND CHAR_LENGTH(TRIM(last_name)) > 0),
    CONSTRAINT ck_patient_sex CHECK (sex IN ('FEMALE','MALE','UNKNOWN','NOT_RECORDED')),
    CONSTRAINT ck_patient_status CHECK (patient_status IN ('ACTIVE','INACTIVE','DECEASED','MERGED')),
    CONSTRAINT ck_patient_blood_group CHECK (blood_group IS NULL OR blood_group IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
    CONSTRAINT fk_patients_created_by_13 FOREIGN KEY (created_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;

CREATE TABLE patient_contacts (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    patient_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contact_type        VARCHAR(24) NOT NULL,
    contact_name        VARCHAR(160),
    relationship        VARCHAR(60),
    phone_number        VARCHAR(32),
    email               VARCHAR(254),
    is_primary          TINYINT(1) NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_patient_contact_type CHECK (contact_type IN ('PATIENT','EMERGENCY','GUARDIAN','OTHER')),
    CONSTRAINT ck_patient_contact_value CHECK (phone_number IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT fk_patient_contacts_patient_id_14 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE patient_allergies (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    patient_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    allergen_name       VARCHAR(160) NOT NULL,
    reaction            VARCHAR(500),
    severity            VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    recorded_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    recorded_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_allergy_severity CHECK (severity IN ('MILD','MODERATE','SEVERE','UNKNOWN')),
    CONSTRAINT ck_allergy_verification CHECK (verification_status IN ('UNVERIFIED','CONFIRMED','ENTERED_IN_ERROR')),
    CONSTRAINT fk_patient_allergies_patient_id_15 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_allergies_recorded_by_16 FOREIGN KEY (recorded_by) REFERENCES staff_members (id)

) ENGINE=InnoDB;

CREATE TABLE appointments (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    patient_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    doctor_staff_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    department_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_at           DATETIME(6) NOT NULL,
    ends_at             DATETIME(6) NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    created_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_appointment_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_appointment_status CHECK (status IN ('BOOKED','CONFIRMED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT fk_appointments_branch_id_17 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id),
    CONSTRAINT fk_appointments_patient_id_18 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_doctor_staff_id_19 FOREIGN KEY (doctor_staff_id) REFERENCES doctor_profiles (staff_id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_department_id_20 FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_created_by_21 FOREIGN KEY (created_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_appointments_patient_start ON appointments(patient_id, starts_at DESC);
CREATE INDEX ix_appointments_department_start ON appointments(department_id, starts_at);
CREATE INDEX ix_appointments_doctor_start ON appointments(doctor_staff_id, starts_at);

CREATE TABLE encounters (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    patient_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    appointment_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    department_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attending_doctor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encounter_type      VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT',
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    chief_complaint     VARCHAR(1000),
    started_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at            DATETIME(6),
    created_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT ck_encounter_type CHECK (encounter_type IN ('OUTPATIENT','EMERGENCY','INPATIENT','FOLLOW_UP')),
    CONSTRAINT ck_encounter_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_encounter_time CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT fk_encounters_branch_id_22 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id),
    CONSTRAINT fk_encounters_patient_id_23 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_appointment_id_24 FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_department_id_25 FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_attending_doctor_id_26 FOREIGN KEY (attending_doctor_id) REFERENCES doctor_profiles (staff_id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounters_created_by_27 FOREIGN KEY (created_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_encounters_patient_started ON encounters(patient_id, started_at DESC);

CREATE TABLE vital_signs (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    measured_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    measured_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    temperature_c      DECIMAL(4,1),
    systolic_mmhg       SMALLINT,
    diastolic_mmhg      SMALLINT,
    pulse_bpm           SMALLINT,
    respiratory_rate    SMALLINT,
    oxygen_saturation   DECIMAL(5,2),
    weight_kg           DECIMAL(6,2),
    height_cm           DECIMAL(6,2),
    notes               VARCHAR(500),
    CONSTRAINT ck_vitals_temperature CHECK (temperature_c IS NULL OR temperature_c BETWEEN 25 AND 45),
    CONSTRAINT ck_vitals_bp CHECK ((systolic_mmhg IS NULL OR systolic_mmhg BETWEEN 20 AND 300)
                               AND (diastolic_mmhg IS NULL OR diastolic_mmhg BETWEEN 10 AND 200)),
    CONSTRAINT ck_vitals_pulse CHECK (pulse_bpm IS NULL OR pulse_bpm BETWEEN 1 AND 300),
    CONSTRAINT ck_vitals_resp CHECK (respiratory_rate IS NULL OR respiratory_rate BETWEEN 1 AND 100),
    CONSTRAINT ck_vitals_spo2 CHECK (oxygen_saturation IS NULL OR oxygen_saturation BETWEEN 0 AND 100),
    CONSTRAINT ck_vitals_weight CHECK (weight_kg IS NULL OR weight_kg > 0),
    CONSTRAINT ck_vitals_height CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT fk_vital_signs_encounter_id_28 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vital_signs_measured_by_29 FOREIGN KEY (measured_by) REFERENCES staff_members (id)

) ENGINE=InnoDB;

CREATE TABLE clinical_notes (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_staff_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    note_type           VARCHAR(24) NOT NULL,
    note_text           TEXT NOT NULL,
    note_status         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    signed_at           DATETIME(6),
    amended_from_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT ck_clinical_note_type CHECK (note_type IN ('HISTORY','EXAMINATION','ASSESSMENT','PLAN','PROGRESS','DISCHARGE','OTHER')),
    CONSTRAINT ck_clinical_note_status CHECK (note_status IN ('DRAFT','SIGNED','AMENDED')),
    CONSTRAINT ck_signed_note_time CHECK (note_status = 'DRAFT' OR signed_at IS NOT NULL),
    CONSTRAINT ck_clinical_note_nonblank CHECK (CHAR_LENGTH(TRIM(note_text)) > 0),
    CONSTRAINT fk_clinical_notes_encounter_id_30 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_notes_author_staff_id_31 FOREIGN KEY (author_staff_id) REFERENCES staff_members (id),
    CONSTRAINT fk_clinical_notes_amended_from_id_32 FOREIGN KEY (amended_from_id) REFERENCES clinical_notes (id)

) ENGINE=InnoDB;
CREATE INDEX ix_clinical_notes_encounter ON clinical_notes(encounter_id, created_at);

CREATE TABLE diagnosis_catalog (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code_system         VARCHAR(32) NOT NULL,
    diagnosis_code      VARCHAR(32) NOT NULL,
    description         VARCHAR(300) NOT NULL,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_diagnosis_code UNIQUE (code_system, diagnosis_code)

) ENGINE=InnoDB;

CREATE TABLE encounter_diagnoses (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    diagnosis_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recorded_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_primary          TINYINT(1) NOT NULL DEFAULT FALSE,
    clinical_status     VARCHAR(20) NOT NULL DEFAULT 'PROVISIONAL',
    recorded_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_encounter_diagnosis UNIQUE (encounter_id, diagnosis_id),
    CONSTRAINT ck_diagnosis_status CHECK (clinical_status IN ('PROVISIONAL','CONFIRMED','RULED_OUT')),
    CONSTRAINT fk_encounter_diagnoses_encounter_id_33 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounter_diagnoses_diagnosis_id_34 FOREIGN KEY (diagnosis_id) REFERENCES diagnosis_catalog (id) ON DELETE RESTRICT,
    CONSTRAINT fk_encounter_diagnoses_recorded_by_35 FOREIGN KEY (recorded_by) REFERENCES staff_members (id)

) ENGINE=InnoDB;
-- uq_encounter_one_primary_diagnosis recreated with MySQL generated columns in 03_constraints.sql.


-- -----------------------------------------------------------------------------
-- 4. Laboratory
-- -----------------------------------------------------------------------------
CREATE TABLE lab_test_catalog (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code                VARCHAR(32) NOT NULL UNIQUE,
    name                VARCHAR(160) NOT NULL,
    specimen_type       VARCHAR(80),
    result_unit         VARCHAR(40),
    reference_range     VARCHAR(120),
    standard_price      DECIMAL(14,2) NOT NULL DEFAULT 0,
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    expected_minutes    INTEGER,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_lab_test_price CHECK (standard_price >= 0),
    CONSTRAINT ck_lab_test_duration CHECK (expected_minutes IS NULL OR expected_minutes > 0)

) ENGINE=InnoDB;

CREATE TABLE lab_orders (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ordered_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    priority            VARCHAR(12) NOT NULL DEFAULT 'ROUTINE',
    status              VARCHAR(20) NOT NULL DEFAULT 'ORDERED',
    clinical_question   VARCHAR(500),
    ordered_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_lab_order_priority CHECK (priority IN ('ROUTINE','URGENT','STAT')),
    CONSTRAINT ck_lab_order_status CHECK (status IN ('ORDERED','ACCEPTED','IN_PROGRESS','PARTIALLY_COMPLETED','COMPLETED','CANCELLED')),
    CONSTRAINT fk_lab_orders_encounter_id_36 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_orders_ordered_by_37 FOREIGN KEY (ordered_by) REFERENCES doctor_profiles (staff_id)

) ENGINE=InnoDB;

CREATE TABLE lab_order_items (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    lab_order_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lab_test_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    specimen_code       VARCHAR(48),
    collected_at        DATETIME(6),
    collected_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT ck_lab_item_status CHECK (status IN ('PENDING','COLLECTED','PROCESSING','RESULTED','REVIEWED','CANCELLED')),
    CONSTRAINT fk_lab_order_items_lab_order_id_38 FOREIGN KEY (lab_order_id) REFERENCES lab_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_order_items_lab_test_id_39 FOREIGN KEY (lab_test_id) REFERENCES lab_test_catalog (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_order_items_collected_by_40 FOREIGN KEY (collected_by) REFERENCES staff_members (id)

) ENGINE=InnoDB;

CREATE TABLE lab_results (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    lab_order_item_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    result_text         TEXT,
    result_numeric      DECIMAL(16,5),
    result_unit         VARCHAR(40),
    reference_range     VARCHAR(120),
    abnormal_flag       VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',
    performed_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    reviewed_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    performed_at        DATETIME(6),
    reviewed_at         DATETIME(6),
    released_at         DATETIME(6),
    CONSTRAINT ck_lab_result_value CHECK (result_text IS NOT NULL OR result_numeric IS NOT NULL),
    CONSTRAINT ck_lab_abnormal_flag CHECK (abnormal_flag IN ('LOW','NORMAL','HIGH','CRITICAL','UNKNOWN')),
    CONSTRAINT ck_lab_review_pair CHECK ((reviewed_by IS NULL AND reviewed_at IS NULL) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT fk_lab_results_lab_order_item_id_41 FOREIGN KEY (lab_order_item_id) REFERENCES lab_order_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_results_performed_by_42 FOREIGN KEY (performed_by) REFERENCES staff_members (id),
    CONSTRAINT fk_lab_results_reviewed_by_43 FOREIGN KEY (reviewed_by) REFERENCES staff_members (id)

) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 5. Pharmacy and inventory ledger
-- -----------------------------------------------------------------------------
CREATE TABLE medications (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code                VARCHAR(40) NOT NULL UNIQUE,
    generic_name        VARCHAR(160) NOT NULL,
    brand_name          VARCHAR(160),
    strength            VARCHAR(80),
    dosage_form         VARCHAR(60),
    route               VARCHAR(60),
    dispensing_unit     VARCHAR(40) NOT NULL DEFAULT 'unit',
    is_controlled      TINYINT(1) NOT NULL DEFAULT FALSE,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE

) ENGINE=InnoDB;

CREATE TABLE inventory_locations (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code                VARCHAR(32) NOT NULL,
    name                VARCHAR(140) NOT NULL,
    location_type       VARCHAR(20) NOT NULL,
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_inventory_location_code UNIQUE (branch_id, code),
    CONSTRAINT ck_inventory_location_type CHECK (location_type IN ('MAIN_STORE','PHARMACY','WARD','LAB','OTHER')),
    CONSTRAINT fk_inventory_locations_branch_id_44 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id)

) ENGINE=InnoDB;

CREATE TABLE medication_batches (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    medication_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_number        VARCHAR(80) NOT NULL,
    manufacturer       VARCHAR(160),
    manufactured_on     DATE,
    expires_on          DATE NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_medication_batch UNIQUE (medication_id, batch_number),
    CONSTRAINT ck_batch_dates CHECK (manufactured_on IS NULL OR expires_on >= manufactured_on),
    CONSTRAINT fk_medication_batches_medication_id_45 FOREIGN KEY (medication_id) REFERENCES medications (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE stock_balances (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    location_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity_on_hand    DECIMAL(14,3) NOT NULL DEFAULT 0,
    quantity_reserved   DECIMAL(14,3) NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_stock_location_batch UNIQUE (location_id, batch_id),
    CONSTRAINT ck_stock_quantities CHECK (quantity_on_hand >= 0 AND quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand),
    CONSTRAINT fk_stock_balances_location_id_46 FOREIGN KEY (location_id) REFERENCES inventory_locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_balances_batch_id_47 FOREIGN KEY (batch_id) REFERENCES medication_batches (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE stock_movements (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    location_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    movement_type       VARCHAR(20) NOT NULL,
    quantity_delta      DECIMAL(14,3) NOT NULL,
    reference_type      VARCHAR(40),
    reference_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    transfer_group_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    reason              VARCHAR(300),
    performed_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('RECEIPT','DISPENSE','TRANSFER_IN','TRANSFER_OUT','ADJUSTMENT','RETURN','EXPIRED','DAMAGE')),
    CONSTRAINT ck_stock_delta_nonzero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_movement_direction CHECK (
        (movement_type IN ('RECEIPT','TRANSFER_IN','RETURN') AND quantity_delta > 0)
        OR (movement_type IN ('DISPENSE','TRANSFER_OUT','EXPIRED','DAMAGE') AND quantity_delta < 0)
        OR movement_type = 'ADJUSTMENT'
    ),
    CONSTRAINT fk_stock_movements_location_id_48 FOREIGN KEY (location_id) REFERENCES inventory_locations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_batch_id_49 FOREIGN KEY (batch_id) REFERENCES medication_batches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_performed_by_50 FOREIGN KEY (performed_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_stock_movements_batch_time ON stock_movements(batch_id, occurred_at DESC);

CREATE TABLE prescriptions (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prescriber_staff_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ordered_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cancelled_at        DATETIME(6),
    cancelled_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    CONSTRAINT ck_prescription_status CHECK (status IN ('ACTIVE','PARTIALLY_DISPENSED','DISPENSED','CANCELLED')),
    CONSTRAINT fk_prescriptions_encounter_id_51 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescriptions_prescriber_staff_id_52 FOREIGN KEY (prescriber_staff_id) REFERENCES doctor_profiles (staff_id),
    CONSTRAINT fk_prescriptions_cancelled_by_53 FOREIGN KEY (cancelled_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;

CREATE TABLE prescription_items (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    prescription_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    medication_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dose                VARCHAR(80) NOT NULL,
    route               VARCHAR(60),
    frequency           VARCHAR(100) NOT NULL,
    duration_days       INTEGER,
    quantity_prescribed DECIMAL(14,3) NOT NULL,
    instructions        VARCHAR(500),
    CONSTRAINT ck_prescription_quantity CHECK (quantity_prescribed > 0),
    CONSTRAINT ck_prescription_duration CHECK (duration_days IS NULL OR duration_days > 0),
    CONSTRAINT fk_prescription_items_prescription_id_54 FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_prescription_items_medication_id_55 FOREIGN KEY (medication_id) REFERENCES medications (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE dispenses (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    prescription_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dispensed_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inventory_location_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    dispensed_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_dispense_status CHECK (status IN ('DRAFT','POSTED','VOID')),
    CONSTRAINT fk_dispenses_prescription_id_56 FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dispenses_dispensed_by_57 FOREIGN KEY (dispensed_by) REFERENCES staff_members (id),
    CONSTRAINT fk_dispenses_inventory_location_id_58 FOREIGN KEY (inventory_location_id) REFERENCES inventory_locations (id)

) ENGINE=InnoDB;

CREATE TABLE dispense_items (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    dispense_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prescription_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity_dispensed  DECIMAL(14,3) NOT NULL,
    CONSTRAINT uq_dispense_item_batch UNIQUE (dispense_id, prescription_item_id, batch_id),
    CONSTRAINT ck_dispense_item_quantity CHECK (quantity_dispensed > 0),
    CONSTRAINT fk_dispense_items_dispense_id_59 FOREIGN KEY (dispense_id) REFERENCES dispenses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dispense_items_prescription_item_id_60 FOREIGN KEY (prescription_item_id) REFERENCES prescription_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dispense_items_batch_id_61 FOREIGN KEY (batch_id) REFERENCES medication_batches (id) ON DELETE RESTRICT

) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 6. Service catalogue, invoices and payments
-- -----------------------------------------------------------------------------
CREATE TABLE service_catalog (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code                VARCHAR(40) NOT NULL UNIQUE,
    name                VARCHAR(160) NOT NULL,
    service_type        VARCHAR(24) NOT NULL,
    standard_price      DECIMAL(14,2) NOT NULL,
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    is_active           TINYINT(1) NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_service_type CHECK (service_type IN ('CONSULTATION','LABORATORY','RADIOLOGY','PROCEDURE','ROOM','OTHER')),
    CONSTRAINT ck_service_price CHECK (standard_price >= 0)

) ENGINE=InnoDB;

CREATE TABLE invoices (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invoice_number      VARCHAR(40) NOT NULL,
    patient_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encounter_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    currency_code       CHAR(3) NOT NULL DEFAULT 'YER',
    subtotal            DECIMAL(14,2) NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_amount        DECIMAL(14,2) NOT NULL DEFAULT 0,
    issued_at           DATETIME(6),
    created_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_invoice_branch_number UNIQUE (branch_id, invoice_number),
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOID')),
    CONSTRAINT ck_invoice_amounts CHECK (subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_invoice_total CHECK (total_amount = subtotal - discount_amount + tax_amount),
    CONSTRAINT fk_invoices_branch_id_62 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id),
    CONSTRAINT fk_invoices_patient_id_63 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_encounter_id_64 FOREIGN KEY (encounter_id) REFERENCES encounters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_created_by_65 FOREIGN KEY (created_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;

CREATE TABLE invoice_items (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    invoice_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    service_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    dispense_item_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    description_snapshot VARCHAR(240) NOT NULL,
    quantity            DECIMAL(14,3) NOT NULL,
    unit_price          DECIMAL(14,2) NOT NULL,
    line_discount       DECIMAL(14,2) NOT NULL DEFAULT 0,
    line_total          DECIMAL(14,2) NOT NULL,
    CONSTRAINT ck_invoice_item_source CHECK ((service_id IS NOT NULL) <> (dispense_item_id IS NOT NULL)),
    CONSTRAINT ck_invoice_item_amounts CHECK (quantity > 0 AND unit_price >= 0 AND line_discount >= 0 AND line_total >= 0),
    CONSTRAINT fk_invoice_items_invoice_id_66 FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_items_service_id_67 FOREIGN KEY (service_id) REFERENCES service_catalog (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_items_dispense_item_id_68 FOREIGN KEY (dispense_item_id) REFERENCES dispense_items (id) ON DELETE RESTRICT

) ENGINE=InnoDB;
CREATE UNIQUE INDEX uq_dispense_item_invoiced_once ON invoice_items(dispense_item_id);

CREATE TABLE payments (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    invoice_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_number      VARCHAR(40) NOT NULL UNIQUE,
    amount              DECIMAL(14,2) NOT NULL,
    currency_code       CHAR(3) NOT NULL,
    payment_method      VARCHAR(20) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    transaction_reference VARCHAR(120),
    received_by         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    paid_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','INSURANCE','OTHER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('POSTED','VOID','REFUNDED')),
    CONSTRAINT fk_payments_invoice_id_69 FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_received_by_70 FOREIGN KEY (received_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_payments_invoice_time ON payments(invoice_id, paid_at DESC);

-- -----------------------------------------------------------------------------
-- 7. Messaging, notifications, audit and smart operational alerts
-- -----------------------------------------------------------------------------
CREATE TABLE message_outbox (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    event_type          VARCHAR(120) NOT NULL,
    aggregate_type      VARCHAR(80) NOT NULL,
    aggregate_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key     VARCHAR(160) NOT NULL UNIQUE,
    payload             JSON NOT NULL,
    delivery_status     VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at        DATETIME(6),
    last_error_code     VARCHAR(80),
    CONSTRAINT ck_outbox_status CHECK (delivery_status IN ('PENDING','PROCESSING','PUBLISHED','DEAD_LETTER')),
    CONSTRAINT ck_outbox_attempts CHECK (attempt_count >= 0)

) ENGINE=InnoDB;
CREATE INDEX ix_outbox_pending ON message_outbox(next_attempt_at, created_at);

CREATE TABLE notifications (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    recipient_user_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    notification_type   VARCHAR(40) NOT NULL,
    title               VARCHAR(180) NOT NULL,
    body                VARCHAR(1000) NOT NULL,
    reference_type      VARCHAR(60),
    reference_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    read_at             DATETIME(6),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notifications_recipient_user_id_71 FOREIGN KEY (recipient_user_id) REFERENCES user_accounts (id) ON DELETE CASCADE

) ENGINE=InnoDB;
CREATE INDEX ix_notifications_user_unread ON notifications(recipient_user_id, created_at DESC);

CREATE TABLE audit_logs (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    actor_user_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    action_code         VARCHAR(80) NOT NULL,
    resource_type       VARCHAR(80) NOT NULL,
    resource_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    outcome             VARCHAR(16) NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(400),
    metadata            JSON NOT NULL DEFAULT (JSON_OBJECT()),
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILURE')),
    CONSTRAINT fk_audit_logs_actor_user_id_72 FOREIGN KEY (actor_user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_audit_logs_branch_id_73 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id)

) ENGINE=InnoDB;
CREATE INDEX ix_audit_resource_time ON audit_logs(resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_actor_time ON audit_logs(actor_user_id, occurred_at DESC);

CREATE TABLE operational_alerts (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    alert_code          VARCHAR(80) NOT NULL,
    source_module       VARCHAR(48) NOT NULL,
    severity            VARCHAR(12) NOT NULL,
    title               VARCHAR(180) NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    metric_value        DECIMAL(18,4),
    threshold_value     DECIMAL(18,4),
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    generated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged_by     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    acknowledged_at     DATETIME(6),
    resolved_at         DATETIME(6),
    CONSTRAINT ck_alert_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED','DISMISSED')),
    CONSTRAINT fk_operational_alerts_branch_id_74 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id),
    CONSTRAINT fk_operational_alerts_acknowledged_by_75 FOREIGN KEY (acknowledged_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_alerts_open_severity ON operational_alerts(severity, generated_at DESC);

CREATE TABLE system_settings (

    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    setting_key         VARCHAR(100) NOT NULL,
    branch_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    setting_value       JSON NOT NULL,
    description         VARCHAR(300),
    updated_by          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_system_settings_branch_id_76 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id),
    CONSTRAINT fk_system_settings_updated_by_77 FOREIGN KEY (updated_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
-- uq_system_setting_global recreated with MySQL generated columns in 03_constraints.sql.

CREATE UNIQUE INDEX uq_system_setting_branch ON system_settings(setting_key, branch_id);

-- -----------------------------------------------------------------------------
-- 8. Essential supporting indexes
-- -----------------------------------------------------------------------------
CREATE INDEX ix_patients_name ON patients(last_name, first_name);
CREATE INDEX ix_patients_phone ON patients(primary_phone);
CREATE INDEX ix_lab_orders_encounter_date ON lab_orders(encounter_id, ordered_at DESC);
CREATE INDEX ix_lab_items_status ON lab_order_items(status);
CREATE INDEX ix_prescriptions_encounter ON prescriptions(encounter_id, ordered_at DESC);
CREATE INDEX ix_batches_expiry ON medication_batches(expires_on);
CREATE INDEX ix_stock_balances_location ON stock_balances(location_id);
CREATE INDEX ix_invoices_patient_date ON invoices(patient_id, created_at DESC);

-- Implementation notes:
-- 1. Update updated_at in application services or add controlled DB triggers.
-- 2. Never delete signed clinical notes, posted stock movements, posted payments,
--    or audit logs. Use correction/void workflows with new audit events.
-- 3. Keep invoice totals and stock balances transactionally consistent with their
--    detail/ledger rows. Do not update these values from the React client.
-- 4. Appointment exclusion constraint requires PostgreSQL btree_gist extension.
-- 5. Add admission/bed, procurement, insurance, imaging, and accounting-ledger
--    tables in later migrations after their workflows are specified.
