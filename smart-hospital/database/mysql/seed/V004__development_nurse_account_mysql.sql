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
