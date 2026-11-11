-- Smart Hospital | synthetic local development reference data
-- Run only after V001 and V002 on a disposable development database.
BEGIN;

INSERT INTO hospital.hospital_branches (id, code, name, timezone, default_currency, is_active)
VALUES ('10000000-0000-4000-8000-000000000001', 'MAIN', 'Smart Hospital - Main Branch', 'Asia/Aden', 'YER', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hospital.departments (id, branch_id, code, name, department_type, is_active)
VALUES
('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'GEN-CLINIC', 'General Clinic', 'CLINIC', TRUE),
('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'LAB', 'Laboratory', 'LABORATORY', TRUE),
('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'PHARMACY', 'Pharmacy', 'PHARMACY', TRUE),
('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'ER', 'Emergency', 'EMERGENCY', TRUE),
('20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'INPATIENT', 'Inpatient Ward', 'INPATIENT', TRUE),
('20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'FINANCE', 'Finance', 'FINANCE', TRUE)
ON CONFLICT (branch_id, code) DO NOTHING;

INSERT INTO hospital.staff_members (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES ('30000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        '20000000-0000-4000-8000-000000000001',
        'DEMO-DOC-001', 'Demo', 'Doctor', 'General Practitioner', 'ACTIVE', CURRENT_DATE)
ON CONFLICT (branch_id, employee_number) DO NOTHING;

INSERT INTO hospital.doctor_profiles (staff_id, license_number, specialty, qualification, can_order_labs, is_active)
VALUES ('30000000-0000-4000-8000-000000000001', 'DEMO-LICENSE-001', 'General Medicine', 'Synthetic demo profile', TRUE, TRUE)
ON CONFLICT (staff_id) DO NOTHING;

INSERT INTO hospital.roles (code, name, description, is_system_role)
VALUES
('HOSPITAL_ADMIN', 'Hospital Administrator', 'Full configuration and administrative access', TRUE),
('HOSPITAL_MANAGER', 'Hospital Manager', 'Operational oversight and approved reports', TRUE),
('RECEPTIONIST', 'Receptionist', 'Patient registration and appointment scheduling', TRUE),
('DOCTOR', 'Doctor', 'Clinical encounter and authorized patient record access', TRUE),
('NURSE', 'Nurse', 'Nursing observations and assigned clinical workflows', TRUE),
('LAB_TECHNICIAN', 'Lab Technician', 'Laboratory order processing and result entry', TRUE),
('PHARMACIST', 'Pharmacist', 'Medication verification and dispensing', TRUE),
('ACCOUNTANT', 'Accountant', 'Billing and payment workflows', TRUE),
('INVENTORY_OFFICER', 'Inventory Officer', 'Stock and purchasing workflows', TRUE)
ON CONFLICT (code) DO NOTHING;

COMMIT;
