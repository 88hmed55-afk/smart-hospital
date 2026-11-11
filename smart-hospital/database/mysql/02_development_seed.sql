-- SMART HOSPITAL — synthetic development data for MySQL 8.0.38
USE hospital;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone='+00:00';


-- ===================== V001__development_reference_data_mysql.sql =====================
-- Smart Hospital | synthetic local development reference data
-- Run only after V001 and V002 on a disposable development database.

INSERT IGNORE INTO hospital.hospital_branches (id, code, name, timezone, default_currency, is_active)
VALUES ('10000000-0000-4000-8000-000000000001', 'MAIN', 'Smart Hospital - Main Branch', 'Asia/Aden', 'YER', TRUE);

INSERT IGNORE INTO hospital.departments (id, branch_id, code, name, department_type, is_active)
VALUES
('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'GEN-CLINIC', 'General Clinic', 'CLINIC', TRUE),
('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'LAB', 'Laboratory', 'LABORATORY', TRUE),
('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'PHARMACY', 'Pharmacy', 'PHARMACY', TRUE),
('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'ER', 'Emergency', 'EMERGENCY', TRUE),
('20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'INPATIENT', 'Inpatient Ward', 'INPATIENT', TRUE),
('20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'FINANCE', 'Finance', 'FINANCE', TRUE);

INSERT IGNORE INTO hospital.staff_members (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES ('30000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        '20000000-0000-4000-8000-000000000001',
        'DEMO-DOC-001', 'Demo', 'Doctor', 'General Practitioner', 'ACTIVE', CURRENT_DATE);

INSERT IGNORE INTO hospital.doctor_profiles (staff_id, license_number, specialty, qualification, can_order_labs, is_active)
VALUES ('30000000-0000-4000-8000-000000000001', 'DEMO-LICENSE-001', 'General Medicine', 'Synthetic demo profile', TRUE, TRUE);

INSERT IGNORE INTO hospital.roles (code, name, description, is_system_role)
VALUES
('HOSPITAL_ADMIN', 'Hospital Administrator', 'Full configuration and administrative access', TRUE),
('HOSPITAL_MANAGER', 'Hospital Manager', 'Operational oversight and approved reports', TRUE),
('RECEPTIONIST', 'Receptionist', 'Patient registration and appointment scheduling', TRUE),
('DOCTOR', 'Doctor', 'Clinical encounter and authorized patient record access', TRUE),
('NURSE', 'Nurse', 'Nursing observations and assigned clinical workflows', TRUE),
('LAB_TECHNICIAN', 'Lab Technician', 'Laboratory order processing and result entry', TRUE),
('PHARMACIST', 'Pharmacist', 'Medication verification and dispensing', TRUE),
('ACCOUNTANT', 'Accountant', 'Billing and payment workflows', TRUE),
('INVENTORY_OFFICER', 'Inventory Officer', 'Stock and purchasing workflows', TRUE);


-- ===================== V002__development_accounts_mysql.sql =====================
-- Smart Hospital | local development account mappings
-- Password authentication remains in the GlassFish file realm for this increment.
-- password_hash is deliberately a non-secret marker and MUST NOT be used as a credential.
USE hospital;
INSERT IGNORE INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000002',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000001',
     'DEMO-REC-001', 'Demo', 'Receptionist', 'Receptionist', 'ACTIVE', CURRENT_DATE);

-- Fixed IDs make repeatable role/branch mapping possible in a disposable database.
INSERT IGNORE INTO user_accounts (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
 ('40000000-0000-4000-8000-000000000001', NULL, 'hospitaladmin', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE),
 ('40000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000002', 'receptionist1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE),
 ('40000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000001', 'doctor1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000001', r.id FROM roles r WHERE r.code='HOSPITAL_ADMIN'
UNION ALL
SELECT '40000000-0000-4000-8000-000000000002', r.id FROM roles r WHERE r.code='RECEPTIONIST'
UNION ALL
SELECT '40000000-0000-4000-8000-000000000003', r.id FROM roles r WHERE r.code='DOCTOR';

INSERT IGNORE INTO user_branch_access (user_id, branch_id, is_default)
VALUES
 ('40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', TRUE),
 ('40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', TRUE),
 ('40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', TRUE);


-- ===================== V003__synthetic_diagnosis_catalog_mysql.sql =====================
-- DEMONSTRATION DATA ONLY. These are deliberately synthetic labels/codes,
-- not a clinical terminology and not suitable for clinical use.

INSERT IGNORE INTO hospital.diagnosis_catalog (code_system, diagnosis_code, description, is_active)
VALUES
    ('DEMO', 'TEST-DIAG-001', 'Synthetic demonstration diagnosis alpha', TRUE),
    ('DEMO', 'TEST-DIAG-002', 'Synthetic demonstration diagnosis beta', TRUE),
    ('DEMO', 'TEST-DIAG-003', 'Synthetic demonstration diagnosis gamma', TRUE);


-- ===================== V004__development_nurse_account_mysql.sql =====================
-- DEVELOPMENT ONLY: synthetic nurse profile and GlassFish file-realm mapping.
-- Create the matching GlassFish file-realm user with group NURSE as documented in backend/README.md.
USE hospital;
INSERT IGNORE INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000003',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000001',
     'DEMO-NUR-001', 'Demo', 'Nurse', 'Nurse', 'ACTIVE', CURRENT_DATE);

INSERT IGNORE INTO user_accounts (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES ('40000000-0000-4000-8000-000000000004',
        '30000000-0000-4000-8000-000000000003',
        'nurse1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000004', r.id
  FROM roles r WHERE r.code='NURSE';

INSERT IGNORE INTO user_branch_access (user_id, branch_id, is_default)
VALUES ('40000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', TRUE);


-- ===================== V005__development_laboratory_accounts_and_catalog_mysql.sql =====================
-- DEVELOPMENT ONLY: synthetic lab staff, file-realm mappings and lab catalogue rows.
-- No real patient or clinical reference data is included.
USE hospital;
INSERT IGNORE INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000004',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000002',
     'DEMO-LAB-TECH-001', 'Demo', 'Lab Technician', 'Laboratory Technician', 'ACTIVE', CURRENT_DATE),
    ('30000000-0000-4000-8000-000000000005',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000002',
     'DEMO-LAB-SUP-001', 'Demo', 'Lab Supervisor', 'Laboratory Supervisor', 'ACTIVE', CURRENT_DATE);

INSERT IGNORE INTO user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
    ('40000000-0000-4000-8000-000000000005', '30000000-0000-4000-8000-000000000004',
     'labtech1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE),
    ('40000000-0000-4000-8000-000000000006', '30000000-0000-4000-8000-000000000005',
     'labsupervisor1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000005', r.id FROM roles r WHERE r.code='LAB_TECHNICIAN'
UNION ALL
SELECT '40000000-0000-4000-8000-000000000006', r.id FROM roles r WHERE r.code='LAB_SUPERVISOR';

INSERT IGNORE INTO user_branch_access (user_id, branch_id, is_default)
VALUES
    ('40000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', TRUE),
    ('40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', TRUE);

-- Catalogue rows are marked DEMO and use deliberately non-clinical units/ranges.
INSERT IGNORE INTO lab_test_catalog
    (id, code, name, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
VALUES
    ('50000000-0000-4000-8000-000000000001', 'DEMO-LAB-CBC', 'Demo Hematology Panel', 'DEMO-SPECIMEN', 'demo-unit', NULL, 0, 'YER', 60, TRUE),
    ('50000000-0000-4000-8000-000000000002', 'DEMO-LAB-GLU', 'Demo Glucose Test', 'DEMO-SPECIMEN', 'demo-unit', NULL, 0, 'YER', 30, TRUE),
    ('50000000-0000-4000-8000-000000000003', 'DEMO-LAB-CRE', 'Demo Creatinine Test', 'DEMO-SPECIMEN', 'demo-unit', NULL, 0, 'YER', 45, TRUE);


-- ===================== V006__development_pharmacy_accounts_and_stock_mysql.sql =====================
-- DEVELOPMENT ONLY: synthetic pharmacist identity, pharmacy location and non-clinical medication stock.
-- All medicine names/strengths are explicitly DEMO data and must never be used for clinical care.
USE hospital;
INSERT IGNORE INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000006',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000003',
     'DEMO-PHARM-001', 'Demo', 'Pharmacist', 'Pharmacist', 'ACTIVE', CURRENT_DATE);

INSERT IGNORE INTO user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
    ('40000000-0000-4000-8000-000000000007', '30000000-0000-4000-8000-000000000006',
     'pharmacist1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000007', r.id
  FROM roles r WHERE r.code='PHARMACIST';

INSERT IGNORE INTO user_branch_access (user_id, branch_id, is_default)
VALUES ('40000000-0000-4000-8000-000000000007',
        '10000000-0000-4000-8000-000000000001', TRUE);

INSERT IGNORE INTO inventory_locations (id, branch_id, code, name, location_type, is_active)
VALUES ('60000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        'DEMO-PHARMACY-01', 'Demo Pharmacy Stock Location', 'PHARMACY', TRUE);

INSERT IGNORE INTO medications
    (id, code, generic_name, brand_name, strength, dosage_form, route, dispensing_unit, is_controlled, is_active)
VALUES
    ('70000000-0000-4000-8000-000000000001', 'DEMO-MED-001', 'Demo Medication Alpha', NULL,
     'DEMO 10', 'DEMO TABLET', 'DEMO ORAL', 'demo-unit', FALSE, TRUE),
    ('70000000-0000-4000-8000-000000000002', 'DEMO-MED-002', 'Demo Medication Beta', NULL,
     'DEMO 20', 'DEMO CAPSULE', 'DEMO ORAL', 'demo-unit', FALSE, TRUE);

INSERT IGNORE INTO medication_batches (id, medication_id, batch_number, manufacturer, manufactured_on, expires_on)
VALUES
    ('71000000-0000-4000-8000-000000000001', '70000000-0000-4000-8000-000000000001', 'DEMO-ALPHA-OLD', 'DEMO MANUFACTURER', DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 120 DAY)),
    ('71000000-0000-4000-8000-000000000002', '70000000-0000-4000-8000-000000000001', 'DEMO-ALPHA-NEW', 'DEMO MANUFACTURER', DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 300 DAY)),
    ('71000000-0000-4000-8000-000000000003', '70000000-0000-4000-8000-000000000002', 'DEMO-BETA-01', 'DEMO MANUFACTURER', DATE_SUB(CURRENT_DATE, INTERVAL 15 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 240 DAY));

INSERT IGNORE INTO stock_balances (id, location_id, batch_id, quantity_on_hand, quantity_reserved, version)
VALUES
    ('72000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000001', 20.000, 0.000, 0),
    ('72000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000002', 100.000, 0.000, 0),
    ('72000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000003', 60.000, 0.000, 0);

-- Opening balances are represented in the stock ledger; these rows are fixtures only.
INSERT IGNORE INTO stock_movements
    (id, location_id, batch_id, movement_type, quantity_delta, reference_type, reason, occurred_at)
VALUES
    ('73000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000001', 'RECEIPT', 20.000, 'DEMO_SEED', 'Synthetic opening balance', CURRENT_TIMESTAMP(6)),
    ('73000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000002', 'RECEIPT', 100.000, 'DEMO_SEED', 'Synthetic opening balance', CURRENT_TIMESTAMP(6)),
    ('73000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000003', 'RECEIPT', 60.000, 'DEMO_SEED', 'Synthetic opening balance', CURRENT_TIMESTAMP(6));


-- ===================== V007__development_billing_admission_fixtures_mysql.sql =====================
-- Synthetic development fixtures only. Never load real patient data here.
USE hospital;
INSERT IGNORE INTO wards (id, branch_id, department_id, ward_code, ward_name, ward_type, is_active)
VALUES ('50000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        '20000000-0000-4000-8000-000000000005',
        'GEN-WARD-A', 'General Ward A', 'GENERAL', TRUE);

INSERT IGNORE INTO rooms (id, branch_id, ward_id, room_number, room_type, floor_label, is_active)
VALUES
 ('51000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','50000000-0000-4000-8000-000000000001','A-101','STANDARD','1',TRUE),
 ('51000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000001','50000000-0000-4000-8000-000000000001','A-102','PRIVATE','1',TRUE);

INSERT IGNORE INTO beds (id, branch_id, room_id, bed_number, bed_type, operational_status, is_active)
VALUES
 ('52000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','01','STANDARD','AVAILABLE',TRUE),
 ('52000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000001','02','STANDARD','AVAILABLE',TRUE),
 ('52000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000001','51000000-0000-4000-8000-000000000002','01','ELECTRIC','AVAILABLE',TRUE);

INSERT IGNORE INTO staff_members (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES ('30000000-0000-4000-8000-000000000007',
        '10000000-0000-4000-8000-000000000001',
        '20000000-0000-4000-8000-000000000006',
        'DEMO-ACC-001', 'Demo', 'Accountant', 'Accountant', 'ACTIVE', CURRENT_DATE);

INSERT IGNORE INTO user_accounts (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES ('40000000-0000-4000-8000-000000000008', '30000000-0000-4000-8000-000000000007',
        'accountant1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000008', r.id
  FROM roles r WHERE r.code='ACCOUNTANT';

INSERT IGNORE INTO user_branch_access (user_id, branch_id, is_default)
VALUES ('40000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', TRUE);

INSERT IGNORE INTO service_catalog (id, code, name, service_type, standard_price, currency_code, is_active)
VALUES
 ('53000000-0000-4000-8000-000000000001','CONS-GEN','General consultation','CONSULTATION',5000.00,'YER',TRUE),
 ('53000000-0000-4000-8000-000000000002','LAB-CBC','Complete blood count','LABORATORY',3500.00,'YER',TRUE),
 ('53000000-0000-4000-8000-000000000003','XR-CHEST','Chest X-ray','RADIOLOGY',8000.00,'YER',TRUE),
 ('53000000-0000-4000-8000-000000000004','PROC-DRESS','Wound dressing','PROCEDURE',2500.00,'YER',TRUE),
 ('53000000-0000-4000-8000-000000000005','ROOM-GEN','General ward daily charge','ROOM',12000.00,'YER',TRUE);


-- ===================== V008__development_analytics_fixtures_mysql.sql =====================
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


-- ===================== V009__development_supplier_fixture_mysql.sql =====================
-- Smart Hospital V009 local development fixtures.
-- All records are synthetic and intended only for a disposable academic database.
USE hospital;
-- A dedicated OTHER department avoids assigning store staff to clinical departments.
INSERT IGNORE INTO departments (id, branch_id, code, name, department_type, is_active)
VALUES ('20000000-0000-4000-8000-000000000007',
        '10000000-0000-4000-8000-000000000001',
        'STORE', 'Main Store & Procurement', 'OTHER', TRUE);

INSERT IGNORE INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name,
     job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000009',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000007',
     'DEMO-INV-001', 'Demo', 'Inventory Officer', 'Inventory Officer', 'ACTIVE', CURRENT_DATE);

INSERT IGNORE INTO user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
    ('40000000-0000-4000-8000-000000000009',
     '30000000-0000-4000-8000-000000000009',
     'inventoryofficer1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000009', id
  FROM roles WHERE code = 'INVENTORY_OFFICER';

INSERT IGNORE INTO user_branch_access (user_id, branch_id, is_default)
VALUES ('40000000-0000-4000-8000-000000000009',
        '10000000-0000-4000-8000-000000000001', TRUE);

-- Main store is separate from the pharmacy dispensing location.
INSERT IGNORE INTO inventory_locations (id, branch_id, code, name, location_type, is_active)
VALUES ('60000000-0000-4000-8000-000000000002',
        '10000000-0000-4000-8000-000000000001',
        'DEMO-MAIN-STORE', 'Demo Main Medical Store', 'MAIN_STORE', TRUE);

INSERT IGNORE INTO suppliers
    (id, supplier_code, supplier_name, contact_person, phone_number, email,
     address_text, tax_or_registration_no, supplier_status)
VALUES
    ('95000000-0000-4000-8000-000000000001', 'DEMO-SUP-001',
     'مورد المستلزمات الطبية التجريبي', 'مسؤول التوريد التجريبي', '+967700000001',
     'supplier.demo@example.invalid', 'عنوان تجريبي - بيانات غير حقيقية',
     'DEMO-REG-001', 'ACTIVE');


-- ===================== V010__development_chart_of_accounts_mysql.sql =====================
-- Synthetic local-development accounting foundation.
-- This seeds a default chart, demo manager identity and current/next fiscal periods for active branches.
USE hospital;
-- A dedicated synthetic manager account enables approval and oversight scenarios
-- on the stock demo branch. Custom branches still receive their own chart/periods below.
INSERT IGNORE INTO hospital.departments (id, branch_id, code, name, department_type, is_active)
SELECT '20000000-0000-4000-8000-000000000008', b.id,
       'ADMIN', 'Hospital Administration', 'ADMINISTRATION', TRUE
  FROM hospital.hospital_branches b
 WHERE b.id = '10000000-0000-4000-8000-000000000001';

INSERT IGNORE INTO hospital.staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name,
     job_title, employment_status, hired_on)
SELECT '30000000-0000-4000-8000-000000000010', d.branch_id, d.id,
       'DEMO-MGR-001', 'Demo', 'Hospital Manager', 'Hospital Manager', 'ACTIVE', CURRENT_DATE
  FROM hospital.departments d
 WHERE d.branch_id = '10000000-0000-4000-8000-000000000001'
   AND d.code = 'ADMIN';

INSERT IGNORE INTO hospital.user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
SELECT '40000000-0000-4000-8000-000000000010', s.id, 'manager1',
       'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE
  FROM hospital.staff_members s
 WHERE s.branch_id = '10000000-0000-4000-8000-000000000001'
   AND s.employee_number = 'DEMO-MGR-001';

INSERT IGNORE INTO hospital.user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM hospital.user_accounts u
  JOIN hospital.roles r ON r.code = 'HOSPITAL_MANAGER'
 WHERE u.username = 'manager1';

INSERT IGNORE INTO hospital.user_branch_access (user_id, branch_id, is_default)
SELECT u.id, b.id, TRUE
  FROM hospital.user_accounts u
  JOIN hospital.hospital_branches b
    ON b.id = '10000000-0000-4000-8000-000000000001'
 WHERE u.username = 'manager1';

INSERT IGNORE INTO hospital.chart_of_accounts
    (branch_id, account_code, account_name, account_type, normal_balance,
     is_control_account, allows_posting, is_active)
SELECT b.id, a.account_code, a.account_name, a.account_type, a.normal_balance,
       FALSE, TRUE, TRUE
  FROM hospital.hospital_branches b
 CROSS JOIN (
    SELECT '1000' AS account_code, 'Cash on Hand' AS account_name, 'ASSET' AS account_type, 'DEBIT' AS normal_balance
    UNION ALL SELECT '1010', 'Bank and Electronic Receipts', 'ASSET', 'DEBIT'
    UNION ALL SELECT '1100', 'Accounts Receivable', 'ASSET', 'DEBIT'
    UNION ALL SELECT '1200', 'Pharmacy Inventory', 'ASSET', 'DEBIT'
    UNION ALL SELECT '2100', 'Goods Received Not Invoiced', 'LIABILITY', 'CREDIT'
    UNION ALL SELECT '2150', 'Accounts Payable', 'LIABILITY', 'CREDIT'
    UNION ALL SELECT '2200', 'Tax Payable', 'LIABILITY', 'CREDIT'
    UNION ALL SELECT '3000', 'Opening Equity', 'EQUITY', 'CREDIT'
    UNION ALL SELECT '4100', 'Clinical Service Revenue', 'REVENUE', 'CREDIT'
    UNION ALL SELECT '4200', 'Discounts Allowed', 'EXPENSE', 'DEBIT'
    UNION ALL SELECT '5100', 'Pharmacy Cost of Goods Sold', 'EXPENSE', 'DEBIT'
    UNION ALL SELECT '5200', 'Operating Expenses', 'EXPENSE', 'DEBIT'
 ) AS a
 WHERE b.is_active = TRUE;

INSERT IGNORE INTO hospital.fiscal_periods
    (branch_id, period_code, period_name, starts_on, ends_on, period_status)
SELECT b.id,
       CONCAT('FY', YEAR(CURRENT_DATE)),
       CONCAT('Fiscal Year ', YEAR(CURRENT_DATE)),
       MAKEDATE(YEAR(CURRENT_DATE), 1),
       MAKEDATE(YEAR(CURRENT_DATE) + 1, 1),
       'OPEN'
  FROM hospital.hospital_branches b
 WHERE b.is_active = TRUE
UNION ALL
SELECT b.id,
       CONCAT('FY', YEAR(CURRENT_DATE) + 1),
       CONCAT('Fiscal Year ', YEAR(CURRENT_DATE) + 1),
       MAKEDATE(YEAR(CURRENT_DATE) + 1, 1),
       MAKEDATE(YEAR(CURRENT_DATE) + 2, 1),
       'OPEN'
  FROM hospital.hospital_branches b
 WHERE b.is_active = TRUE;
