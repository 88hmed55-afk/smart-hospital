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
