\set ON_ERROR_STOP on
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM hospital.user_accounts WHERE username='labtech1' AND account_status='ACTIVE') THEN
        RAISE EXCEPTION 'Synthetic lab technician account is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM hospital.user_accounts WHERE username='labsupervisor1' AND account_status='ACTIVE') THEN
        RAISE EXCEPTION 'Synthetic lab supervisor account is missing';
    END IF;
    IF (SELECT count(*) FROM hospital.lab_test_catalog WHERE code LIKE 'DEMO-LAB-%' AND is_active) <> 3 THEN
        RAISE EXCEPTION 'Expected three synthetic DEMO lab catalogue entries';
    END IF;
    IF EXISTS (
        SELECT 1 FROM hospital.staff_members sm
        WHERE sm.id IN ('30000000-0000-4000-8000-000000000004','30000000-0000-4000-8000-000000000005')
          AND (sm.department_id IS NULL OR NOT EXISTS (
              SELECT 1 FROM hospital.departments d WHERE d.id=sm.department_id AND d.department_type='LABORATORY'))
    ) THEN
        RAISE EXCEPTION 'Synthetic laboratory staff must belong to an active laboratory department';
    END IF;
END $$;
SELECT 'Synthetic laboratory seed validation passed.' AS validation_result;
