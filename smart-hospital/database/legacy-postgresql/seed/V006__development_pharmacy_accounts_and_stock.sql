-- DEVELOPMENT ONLY: synthetic pharmacist identity, pharmacy location and non-clinical medication stock.
-- All medicine names/strengths are explicitly DEMO data and must never be used for clinical care.
BEGIN;
SET search_path TO hospital, public;

INSERT INTO staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name, job_title, employment_status, hired_on)
VALUES
    ('30000000-0000-4000-8000-000000000006',
     '10000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000003',
     'DEMO-PHARM-001', 'Demo', 'Pharmacist', 'Pharmacist', 'ACTIVE', CURRENT_DATE)
ON CONFLICT (branch_id, employee_number) DO NOTHING;

INSERT INTO user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
VALUES
    ('40000000-0000-4000-8000-000000000007', '30000000-0000-4000-8000-000000000006',
     'pharmacist1', 'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT '40000000-0000-4000-8000-000000000007'::uuid, r.id
  FROM roles r WHERE r.code='PHARMACIST'
ON CONFLICT DO NOTHING;

INSERT INTO user_branch_access (user_id, branch_id, is_default)
VALUES ('40000000-0000-4000-8000-000000000007',
        '10000000-0000-4000-8000-000000000001', TRUE)
ON CONFLICT (user_id, branch_id) DO NOTHING;

INSERT INTO inventory_locations (id, branch_id, code, name, location_type, is_active)
VALUES ('60000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        'DEMO-PHARMACY-01', 'Demo Pharmacy Stock Location', 'PHARMACY', TRUE)
ON CONFLICT (branch_id, code) DO NOTHING;

INSERT INTO medications
    (id, code, generic_name, brand_name, strength, dosage_form, route, dispensing_unit, is_controlled, is_active)
VALUES
    ('70000000-0000-4000-8000-000000000001', 'DEMO-MED-001', 'Demo Medication Alpha', NULL,
     'DEMO 10', 'DEMO TABLET', 'DEMO ORAL', 'demo-unit', FALSE, TRUE),
    ('70000000-0000-4000-8000-000000000002', 'DEMO-MED-002', 'Demo Medication Beta', NULL,
     'DEMO 20', 'DEMO CAPSULE', 'DEMO ORAL', 'demo-unit', FALSE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO medication_batches (id, medication_id, batch_number, manufacturer, manufactured_on, expires_on)
VALUES
    ('71000000-0000-4000-8000-000000000001', '70000000-0000-4000-8000-000000000001', 'DEMO-ALPHA-OLD', 'DEMO MANUFACTURER', CURRENT_DATE - 30, CURRENT_DATE + 120),
    ('71000000-0000-4000-8000-000000000002', '70000000-0000-4000-8000-000000000001', 'DEMO-ALPHA-NEW', 'DEMO MANUFACTURER', CURRENT_DATE - 10, CURRENT_DATE + 300),
    ('71000000-0000-4000-8000-000000000003', '70000000-0000-4000-8000-000000000002', 'DEMO-BETA-01', 'DEMO MANUFACTURER', CURRENT_DATE - 15, CURRENT_DATE + 240)
ON CONFLICT (medication_id, batch_number) DO NOTHING;

INSERT INTO stock_balances (id, location_id, batch_id, quantity_on_hand, quantity_reserved, version)
VALUES
    ('72000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000001', 20.000, 0.000, 0),
    ('72000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000002', 100.000, 0.000, 0),
    ('72000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000003', 60.000, 0.000, 0)
ON CONFLICT (location_id, batch_id) DO NOTHING;

-- Opening balances are represented in the stock ledger; these rows are fixtures only.
INSERT INTO stock_movements
    (id, location_id, batch_id, movement_type, quantity_delta, reference_type, reason, occurred_at)
VALUES
    ('73000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000001', 'RECEIPT', 20.000, 'DEMO_SEED', 'Synthetic opening balance', now()),
    ('73000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000002', 'RECEIPT', 100.000, 'DEMO_SEED', 'Synthetic opening balance', now()),
    ('73000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000003', 'RECEIPT', 60.000, 'DEMO_SEED', 'Synthetic opening balance', now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
