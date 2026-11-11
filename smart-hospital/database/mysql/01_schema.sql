-- SMART HOSPITAL — MySQL 8.0.38 schema
-- Runtime: JDK 17 + GlassFish 7.0.23 + Jakarta EE 10
-- Storage: InnoDB, utf8mb4, UTC DATETIME(6), UUIDs as CHAR(36).
-- This install path targets a NEW, EMPTY MySQL database named `hospital` only.
-- Do NOT execute PostgreSQL scripts under database/legacy-postgresql/migrations against MySQL.
CREATE DATABASE IF NOT EXISTS hospital CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE hospital;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';



-- ===================== V001__hospital_core_mysql.sql =====================
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


-- ===================== V002__admissions_procurement_analytics_accounting_mysql.sql =====================
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
    received_quantity   DECIMAL(14,3) NOT NULL DEFAULT 0,
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
    CONSTRAINT ck_po_item_received_quantity CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
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


-- ===================== V003__patient_branch_access_mysql.sql =====================
-- Smart Hospital | V003 | Branch-scoped patient access
-- Apply only after V001 and V002 on a development database.
USE hospital;
CREATE TABLE patient_branch_access (

    id              CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    patient_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    access_reason   VARCHAR(160) NOT NULL DEFAULT 'CARE',
    granted_by      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    granted_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at      DATETIME(6),
    CONSTRAINT uq_patient_branch_access UNIQUE (patient_id, branch_id),
    CONSTRAINT ck_patient_branch_access_reason CHECK (CHAR_LENGTH(TRIM(access_reason)) > 0),
    CONSTRAINT ck_patient_branch_access_revocation CHECK (revoked_at IS NULL OR revoked_at >= granted_at),
    CONSTRAINT fk_patient_branch_access_patient_id_1 FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_branch_access_branch_id_2 FOREIGN KEY (branch_id) REFERENCES hospital_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_patient_branch_access_granted_by_3 FOREIGN KEY (granted_by) REFERENCES user_accounts (id)

) ENGINE=InnoDB;
CREATE INDEX ix_patient_branch_access_branch_patient
    ON patient_branch_access(branch_id, patient_id);


-- ===================== V004__clinical_workflow_integrity_mysql.sql =====================
-- Smart Hospital | V004 | Clinical workflow integrity
-- Apply only after V001, V002 and V003.
-- This migration adds database-level cross-table safeguards for clinical records.
USE hospital;
-- An encounter must be associated with a patient who has an explicit branch access row.
ALTER TABLE hospital.encounters
    ADD CONSTRAINT fk_encounter_patient_branch_access
    FOREIGN KEY (patient_id, branch_id)
    REFERENCES hospital.patient_branch_access(patient_id, branch_id)
    ON DELETE RESTRICT;

CREATE INDEX ix_encounters_branch_status_started
    ON hospital.encounters(branch_id, status, started_at DESC);
CREATE INDEX ix_vital_signs_encounter_measured
    ON hospital.vital_signs(encounter_id, measured_at DESC);
CREATE INDEX ix_clinical_notes_encounter_created
    ON hospital.clinical_notes(encounter_id, created_at DESC);
CREATE INDEX ix_encounter_diagnoses_encounter
    ON hospital.encounter_diagnoses(encounter_id, is_primary DESC, recorded_at);
CREATE INDEX ix_diagnosis_catalog_description
    ON hospital.diagnosis_catalog (description);

ALTER TABLE hospital.vital_signs
    ADD CONSTRAINT ck_vitals_blood_pressure_pair
        CHECK ((systolic_mmhg IS NULL) = (diastolic_mmhg IS NULL)
               AND (systolic_mmhg IS NULL OR systolic_mmhg > diastolic_mmhg)),
    ADD CONSTRAINT ck_vitals_at_least_one_measurement
        CHECK (temperature_c IS NOT NULL OR systolic_mmhg IS NOT NULL
               OR diastolic_mmhg IS NOT NULL OR pulse_bpm IS NOT NULL
               OR respiratory_rate IS NOT NULL OR oxygen_saturation IS NOT NULL
               OR weight_kg IS NOT NULL OR height_cm IS NOT NULL);

-- Validate branch, doctor, department, patient access and appointment linkage.

-- Clinical observations are append-only. Corrections are captured as new observations.

-- Diagnosis identity is immutable, but its clinical status may be reviewed and changed.

-- Verify author/recorder branch and encounter context for append-only clinical data.

-- Draft notes may be edited before signing. Signed notes cannot be changed or deleted.



-- ===================== V005__care_team_and_laboratory_integrity_mysql.sql =====================
-- Smart Hospital | V005 | Encounter care team and laboratory workflow safeguards
-- MySQL 8.0.38. Apply after V001-V004. Synthetic academic data only.
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

CREATE INDEX ix_lab_orders_status_date ON lab_orders(status, ordered_at DESC);
CREATE INDEX ix_lab_order_items_status ON lab_order_items(status, lab_order_id);

ALTER TABLE notifications ADD COLUMN source_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
CREATE UNIQUE INDEX uq_notifications_event_recipient
    ON notifications(source_event_id, recipient_user_id);

INSERT IGNORE INTO roles (id, code, name, description, is_system_role)
VALUES (UUID(), 'LAB_SUPERVISOR', 'Laboratory Supervisor', 'Reviews and releases laboratory results', TRUE)
;

ALTER TABLE notifications ADD CONSTRAINT fk_notifications_source_event_id_inline FOREIGN KEY (source_event_id) REFERENCES message_outbox (id) ON DELETE RESTRICT;


-- ===================== V006__pharmacy_dispensing_integrity_mysql.sql =====================
-- Smart Hospital | V006 | Prescription uniqueness and idempotent FEFO pharmacy dispensing
-- MySQL 8.0.38. Apply after V001-V005. Synthetic academic data only.
USE hospital;
-- A prescription may contain a medication only once; quantity changes are not handled by duplicate lines.
ALTER TABLE prescription_items
    ADD CONSTRAINT uq_prescription_item_medication UNIQUE (prescription_id, medication_id);

-- Client retries must resolve to the same immutable dispense operation.
ALTER TABLE dispenses
    ADD COLUMN idempotency_key VARCHAR(80),
    ADD COLUMN request_fingerprint CHAR(64);
ALTER TABLE dispenses
    ADD CONSTRAINT ck_dispense_idempotency_pair CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL)
        OR (idempotency_key IS NOT NULL AND REGEXP_LIKE(request_fingerprint, '^[0-9a-f]{64}$', 'c')
            AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 16 AND 80)
    );
CREATE UNIQUE INDEX uq_dispense_idempotency_key
    ON dispenses(idempotency_key);
-- uq_dispense_stock_movement recreated with MySQL generated columns in 03_constraints.sql.

CREATE INDEX ix_dispense_prescription_status_date
    ON dispenses(prescription_id, status, dispensed_at DESC);
CREATE INDEX ix_stock_balance_location_batch
    ON stock_balances(location_id, batch_id);


-- ===================== V007__billing_and_admission_integrity_mysql.sql =====================
-- Smart Hospital | V007 | Billing and admission integrity
-- Target: MySQL 8.0.38
-- Adds database-level safeguards to the existing invoice/payment/admission model.
USE hospital;
-- Refuse to install stronger constraints over inconsistent historical values.
-- PostgreSQL DO-block omitted; corresponding MySQL validation is documented separately.

ALTER TABLE invoice_items
    ADD CONSTRAINT ck_invoice_item_line_math
    CHECK (line_discount <= quantity * unit_price
       AND line_total = round(quantity * unit_price - line_discount, 2));

ALTER TABLE payments
    ADD COLUMN idempotency_key VARCHAR(80),
    ADD COLUMN request_fingerprint CHAR(64);
ALTER TABLE payments
    ADD CONSTRAINT ck_payment_idempotency_pair CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL) OR
        (idempotency_key IS NOT NULL AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 16 AND 80
         AND request_fingerprint IS NOT NULL
         AND REGEXP_LIKE(request_fingerprint, '^[0-9a-f]{64}$', 'c'))
    );
CREATE UNIQUE INDEX uq_payment_idempotency_key
    ON payments (idempotency_key);

CREATE INDEX ix_invoices_branch_status_created
    ON invoices (branch_id, status, created_at DESC);
CREATE INDEX ix_invoices_encounter
    ON invoices (encounter_id);

-- Invoice lines may only be added or changed while the parent invoice is DRAFT.

-- Verify totals before issuing and verify payment-derived status transitions.

-- Serialize payment posting on the invoice row and prevent overpayment/currency drift.

-- No silent edits/deletes of financial receipts. Refunds/voids require a dedicated
-- reversal workflow and supporting audit/ledger records in a later increment.


-- ===================== V008__operational_analytics_and_reorder_policies_mysql.sql =====================
-- Smart Hospital | V008 | Operational analytics and inventory reorder policies
-- Target: MySQL 8.0.38
USE hospital;
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


-- ===================== V009__procurement_receiving_integrity_mysql.sql =====================
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


-- ===================== V010__accounting_posting_guards_mysql.sql =====================
-- Smart Hospital | V010 | General ledger posting integrity
-- Requires V001-V009. Adds database-side protection for account eligibility.
USE hospital;

CREATE INDEX ix_journal_posted_branch_date
    ON hospital.journal_entries (branch_id, entry_date DESC);
CREATE INDEX ix_journal_source_lookup
    ON hospital.journal_entries (branch_id, source_module, source_reference_id);


-- ===================== V011__audit_log_append_only_mysql.sql =====================
USE hospital;

CREATE INDEX ix_audit_branch_time
    ON hospital.audit_logs(branch_id, occurred_at DESC);
