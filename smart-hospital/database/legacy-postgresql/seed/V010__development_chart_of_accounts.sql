-- Synthetic local-development accounting foundation.
-- This seeds a default chart, demo manager identity and current/next fiscal periods for active branches.
BEGIN;
SET search_path TO hospital, public;

-- A dedicated synthetic manager account enables approval and oversight scenarios
-- on the stock demo branch. Custom branches still receive their own chart/periods below.
INSERT INTO hospital.departments (id, branch_id, code, name, department_type, is_active)
SELECT '20000000-0000-4000-8000-000000000008'::uuid, b.id,
       'ADMIN', 'Hospital Administration', 'ADMINISTRATION', TRUE
  FROM hospital.hospital_branches b
 WHERE b.id = '10000000-0000-4000-8000-000000000001'
ON CONFLICT (branch_id, code) DO NOTHING;

INSERT INTO hospital.staff_members
    (id, branch_id, department_id, employee_number, first_name, last_name,
     job_title, employment_status, hired_on)
SELECT '30000000-0000-4000-8000-000000000010'::uuid, d.branch_id, d.id,
       'DEMO-MGR-001', 'Demo', 'Hospital Manager', 'Hospital Manager', 'ACTIVE', CURRENT_DATE
  FROM hospital.departments d
 WHERE d.branch_id = '10000000-0000-4000-8000-000000000001'
   AND d.code = 'ADMIN'
ON CONFLICT (branch_id, employee_number) DO NOTHING;

INSERT INTO hospital.user_accounts
    (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password)
SELECT '40000000-0000-4000-8000-000000000010'::uuid, s.id, 'manager1',
       'GLASSFISH_FILE_REALM_ONLY', 'GLASSFISH_FILE_REALM', 'ACTIVE', TRUE
  FROM hospital.staff_members s
 WHERE s.branch_id = '10000000-0000-4000-8000-000000000001'
   AND s.employee_number = 'DEMO-MGR-001'
ON CONFLICT DO NOTHING;

INSERT INTO hospital.user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM hospital.user_accounts u
  JOIN hospital.roles r ON r.code = 'HOSPITAL_MANAGER'
 WHERE u.username = 'manager1'
ON CONFLICT DO NOTHING;

INSERT INTO hospital.user_branch_access (user_id, branch_id, is_default)
SELECT u.id, b.id, TRUE
  FROM hospital.user_accounts u
  JOIN hospital.hospital_branches b
    ON b.id = '10000000-0000-4000-8000-000000000001'
 WHERE u.username = 'manager1'
ON CONFLICT (user_id, branch_id) DO NOTHING;

INSERT INTO hospital.chart_of_accounts
    (branch_id, account_code, account_name, account_type, normal_balance,
     is_control_account, allows_posting, is_active)
SELECT b.id, a.account_code, a.account_name, a.account_type, a.normal_balance,
       FALSE, TRUE, TRUE
  FROM hospital.hospital_branches b
 CROSS JOIN (VALUES
    ('1000', 'Cash on Hand', 'ASSET', 'DEBIT'),
    ('1010', 'Bank and Electronic Receipts', 'ASSET', 'DEBIT'),
    ('1100', 'Accounts Receivable', 'ASSET', 'DEBIT'),
    ('1200', 'Pharmacy Inventory', 'ASSET', 'DEBIT'),
    ('2100', 'Goods Received Not Invoiced', 'LIABILITY', 'CREDIT'),
    ('2150', 'Accounts Payable', 'LIABILITY', 'CREDIT'),
    ('2200', 'Tax Payable', 'LIABILITY', 'CREDIT'),
    ('3000', 'Opening Equity', 'EQUITY', 'CREDIT'),
    ('4100', 'Clinical Service Revenue', 'REVENUE', 'CREDIT'),
    ('4200', 'Discounts Allowed', 'EXPENSE', 'DEBIT'),
    ('5100', 'Pharmacy Cost of Goods Sold', 'EXPENSE', 'DEBIT'),
    ('5200', 'Operating Expenses', 'EXPENSE', 'DEBIT')
 ) AS a(account_code, account_name, account_type, normal_balance)
 WHERE b.is_active = TRUE
ON CONFLICT (branch_id, account_code) DO NOTHING;

INSERT INTO hospital.fiscal_periods
    (branch_id, period_code, period_name, starts_on, ends_on, period_status)
SELECT b.id,
       'FY' || y.fiscal_year::TEXT,
       'Fiscal Year ' || y.fiscal_year::TEXT,
       make_date(y.fiscal_year, 1, 1),
       make_date(y.fiscal_year + 1, 1, 1),
       'OPEN'
  FROM hospital.hospital_branches b
 CROSS JOIN LATERAL generate_series(EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER,
                                    EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER + 1) AS y(fiscal_year)
 WHERE b.is_active = TRUE
ON CONFLICT (branch_id, period_code) DO NOTHING;

COMMIT;
