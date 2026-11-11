-- Smart Hospital V009 local development fixtures.
-- All records are synthetic and intended only for a disposable academic database.
BEGIN;
SET search_path TO hospital, public;

-- A dedicated OTHER department avoids assigning store staff to clinical departments.
INSERT INTO departments (id, branch_id, code, name, department_type, is_active)
VALUES ('20000000-0000-4000-8000-000000000007',
        '10000000-0000-4000-8000-000000000001',
        'STORE', 'Main Store & Procurement', 'OTHER', TRUE)
ON CONFLICT (branch_id, code) DO NOTHING;

INSERT INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name,
     job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000009',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000007',
     'DEMO-INV-001', 'Demo', 'Inventory Officer', 'Inventory Officer', 'ACTIVE', CURRENT_DATE)
ON CONFLICT (branch_id, employee_number) DO NOTHING;

INSERT INTO user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
    ('40000000-0000-4000-8000-000000000009',
     '30000000-0000-4000-8000-000000000009',
     'inventoryofficer1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000009'::uuid, id
  FROM roles WHERE code = 'INVENTORY_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO user_branch_access (user_id, branch_id, is_default)
VALUES ('40000000-0000-4000-8000-000000000009',
        '10000000-0000-4000-8000-000000000001', TRUE)
ON CONFLICT (user_id, branch_id) DO NOTHING;

-- Main store is separate from the pharmacy dispensing location.
INSERT INTO inventory_locations (id, branch_id, code, name, location_type, is_active)
VALUES ('60000000-0000-4000-8000-000000000002',
        '10000000-0000-4000-8000-000000000001',
        'DEMO-MAIN-STORE', 'Demo Main Medical Store', 'MAIN_STORE', TRUE)
ON CONFLICT (branch_id, code) DO NOTHING;

INSERT INTO suppliers
    (id, supplier_code, supplier_name, contact_person, phone_number, email,
     address_text, tax_or_registration_no, supplier_status)
VALUES
    ('95000000-0000-4000-8000-000000000001', 'DEMO-SUP-001',
     'مورد المستلزمات الطبية التجريبي', 'مسؤول التوريد التجريبي', '+967700000001',
     'supplier.demo@example.invalid', 'عنوان تجريبي - بيانات غير حقيقية',
     'DEMO-REG-001', 'ACTIVE')
ON CONFLICT (supplier_code) DO NOTHING;

COMMIT;
