-- Fail-fast validation for V009 synthetic procurement fixtures.
DO $$
DECLARE
    fixture_count INTEGER;
BEGIN
    SELECT count(*) INTO fixture_count
      FROM hospital.suppliers
     WHERE supplier_code = 'DEMO-SUP-001' AND supplier_status = 'ACTIVE';
    IF fixture_count <> 1 THEN RAISE EXCEPTION 'Expected one active synthetic supplier'; END IF;

    SELECT count(*) INTO fixture_count
      FROM hospital.inventory_locations
     WHERE branch_id = '10000000-0000-4000-8000-000000000001'
       AND code = 'DEMO-MAIN-STORE' AND location_type = 'MAIN_STORE' AND is_active;
    IF fixture_count <> 1 THEN RAISE EXCEPTION 'Expected one active demo MAIN_STORE location'; END IF;

    SELECT count(*) INTO fixture_count
      FROM hospital.user_accounts ua
      JOIN hospital.staff_members sm ON sm.id = ua.staff_id
      JOIN hospital.user_roles ur ON ur.user_id = ua.id
      JOIN hospital.roles r ON r.id = ur.role_id
      JOIN hospital.user_branch_access uba ON uba.user_id = ua.id
     WHERE ua.username = 'inventoryofficer1'
       AND ua.account_status = 'ACTIVE'
       AND sm.employment_status = 'ACTIVE'
       AND r.code = 'INVENTORY_OFFICER'
       AND uba.branch_id = '10000000-0000-4000-8000-000000000001'
       AND uba.is_default;
    IF fixture_count <> 1 THEN RAISE EXCEPTION 'Expected one active inventory officer with role and branch access'; END IF;
END $$;
