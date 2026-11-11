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
