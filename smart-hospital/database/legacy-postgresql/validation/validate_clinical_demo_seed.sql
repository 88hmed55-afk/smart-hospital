\set ON_ERROR_STOP on
DO $$
DECLARE
    v_nurse_count INTEGER;
    v_nurse_role_count INTEGER;
    v_nurse_branch_count INTEGER;
    v_diagnosis_count INTEGER;
BEGIN
    SELECT count(*) INTO v_nurse_count
      FROM hospital.user_accounts ua
      JOIN hospital.staff_members sm ON sm.id=ua.staff_id
     WHERE ua.username='nurse1'
       AND ua.account_status='ACTIVE'
       AND sm.employment_status='ACTIVE';
    IF v_nurse_count <> 1 THEN RAISE EXCEPTION 'Expected one active synthetic nurse account'; END IF;

    SELECT count(*) INTO v_nurse_role_count
      FROM hospital.user_accounts ua
      JOIN hospital.user_roles ur ON ur.user_id=ua.id
      JOIN hospital.roles r ON r.id=ur.role_id
     WHERE ua.username='nurse1' AND r.code='NURSE';
    IF v_nurse_role_count <> 1 THEN RAISE EXCEPTION 'Synthetic nurse role mapping is missing'; END IF;

    SELECT count(*) INTO v_nurse_branch_count
      FROM hospital.user_accounts ua
      JOIN hospital.user_branch_access uba ON uba.user_id=ua.id
     WHERE ua.username='nurse1' AND uba.branch_id='10000000-0000-4000-8000-000000000001';
    IF v_nurse_branch_count <> 1 THEN RAISE EXCEPTION 'Synthetic nurse branch mapping is missing'; END IF;

    SELECT count(*) INTO v_diagnosis_count
      FROM hospital.diagnosis_catalog
     WHERE code_system='DEMO' AND diagnosis_code LIKE 'TEST-DIAG-%' AND is_active=TRUE;
    IF v_diagnosis_count < 3 THEN RAISE EXCEPTION 'Synthetic demonstration diagnosis catalog is incomplete'; END IF;
END $$;
SELECT 'Synthetic clinical test identities and DEMO catalog validation passed.' AS validation_result;
