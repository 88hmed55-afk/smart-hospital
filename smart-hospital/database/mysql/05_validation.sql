-- SMART HOSPITAL MySQL post-install validation. This script is read-only.
USE hospital;
SELECT 'TABLE_COUNT' AS check_name, COUNT(*) AS result
  FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';
SELECT 'FOREIGN_KEY_COUNT' AS check_name, COUNT(*) AS result
  FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE();
SELECT 'USER_ROLE_COUNT' AS check_name, COUNT(*) AS result FROM user_roles;
SELECT 'BRANCH_COUNT' AS check_name, COUNT(*) AS result FROM hospital_branches;
SELECT 'DUPLICATE_DEFAULT_BRANCHES' AS check_name, COUNT(*) AS result
  FROM (SELECT user_id FROM user_branch_access WHERE is_default=1 GROUP BY user_id HAVING COUNT(*)>1) x;
SELECT 'UNBALANCED_POSTED_JOURNALS' AS check_name, COUNT(*) AS result
  FROM (SELECT je.id FROM journal_entries je JOIN journal_lines jl ON jl.journal_entry_id=je.id
        WHERE je.entry_status='POSTED' GROUP BY je.id
        HAVING SUM(jl.debit_amount)<>SUM(jl.credit_amount) OR COUNT(*)<2) x;
