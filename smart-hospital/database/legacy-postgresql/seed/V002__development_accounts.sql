-- Smart Hospital | local development account mappings
-- Password authentication remains in the GlassFish file realm for this increment.
-- password_hash is deliberately a non-secret marker and MUST NOT be used as a credential.
BEGIN;
SET search_path TO hospital, public;

INSERT INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000002',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000001',
     'DEMO-REC-001', 'Demo', 'Receptionist', 'Receptionist', 'ACTIVE', CURRENT_DATE)
ON CONFLICT (branch_id, employee_number) DO NOTHING;

-- Fixed IDs make repeatable role/branch mapping possible in a disposable database.
INSERT INTO user_accounts (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
 ('40000000-0000-4000-8000-000000000001', NULL, 'hospitaladmin', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE),
 ('40000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000002', 'receptionist1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE),
 ('40000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000001', 'doctor1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES
 ('40000000-0000-4000-8000-000000000001'::uuid, 'HOSPITAL_ADMIN'),
 ('40000000-0000-4000-8000-000000000002'::uuid, 'RECEPTIONIST'),
 ('40000000-0000-4000-8000-000000000003'::uuid, 'DOCTOR')
) AS seed(user_id, role_code)
JOIN user_accounts u ON u.id = seed.user_id
JOIN roles r ON r.code = seed.role_code
ON CONFLICT DO NOTHING;

INSERT INTO user_branch_access (user_id, branch_id, is_default)
VALUES
 ('40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', TRUE),
 ('40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', TRUE),
 ('40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', TRUE)
ON CONFLICT (user_id, branch_id) DO NOTHING;

COMMIT;
