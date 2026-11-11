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
