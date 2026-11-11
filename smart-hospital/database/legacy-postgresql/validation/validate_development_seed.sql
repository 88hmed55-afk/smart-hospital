-- Validate the synthetic organization and demo account mapping after all seed scripts.
DO $$
DECLARE
    v_users INTEGER;
    v_roles INTEGER;
    v_branches INTEGER;
    v_doctors INTEGER;
BEGIN
    SELECT count(*) INTO v_users
      FROM hospital.user_accounts
     WHERE username IN ('hospitaladmin', 'receptionist1', 'doctor1')
       AND account_status = 'ACTIVE'
       AND password_scheme = 'GLASSFISH_FILE_REALM';
    IF v_users <> 3 THEN
        RAISE EXCEPTION 'Expected 3 active GlassFish-mapped demo users, found %', v_users;
    END IF;

    SELECT count(*) INTO v_roles
      FROM hospital.user_roles ur
      JOIN hospital.user_accounts ua ON ua.id = ur.user_id
     WHERE ua.username IN ('hospitaladmin', 'receptionist1', 'doctor1');
    IF v_roles <> 3 THEN
        RAISE EXCEPTION 'Expected 3 demo user-role assignments, found %', v_roles;
    END IF;

    SELECT count(*) INTO v_branches
      FROM hospital.user_branch_access uba
      JOIN hospital.user_accounts ua ON ua.id = uba.user_id
     WHERE ua.username IN ('hospitaladmin', 'receptionist1', 'doctor1')
       AND uba.is_default = TRUE;
    IF v_branches <> 3 THEN
        RAISE EXCEPTION 'Expected 3 default branch assignments, found %', v_branches;
    END IF;

    SELECT count(*) INTO v_doctors
      FROM hospital.doctor_profiles dp
      JOIN hospital.staff_members sm ON sm.id = dp.staff_id
     WHERE sm.employee_number = 'DEMO-DOC-001' AND dp.is_active = TRUE;
    IF v_doctors <> 1 THEN
        RAISE EXCEPTION 'Expected one active synthetic demo doctor, found %', v_doctors;
    END IF;

    RAISE NOTICE 'Smart Hospital development identity seed validation passed.';
END $$;
