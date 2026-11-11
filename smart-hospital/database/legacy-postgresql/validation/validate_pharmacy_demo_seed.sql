\set ON_ERROR_STOP on
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM hospital.user_accounts ua
        JOIN hospital.user_roles ur ON ur.user_id=ua.id
        JOIN hospital.roles r ON r.id=ur.role_id AND r.code='PHARMACIST'
        WHERE ua.username='pharmacist1' AND ua.account_status='ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Synthetic pharmacist account and PHARMACIST role are missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM hospital.staff_members sm
        JOIN hospital.departments d ON d.id=sm.department_id
        WHERE sm.employee_number='DEMO-PHARM-001' AND sm.employment_status='ACTIVE'
          AND d.department_type='PHARMACY' AND d.is_active=TRUE
    ) THEN
        RAISE EXCEPTION 'Synthetic pharmacist must be active in the pharmacy department';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM hospital.inventory_locations
        WHERE code='DEMO-PHARMACY-01' AND location_type='PHARMACY' AND is_active=TRUE
    ) THEN
        RAISE EXCEPTION 'Synthetic pharmacy inventory location is missing';
    END IF;
    IF (SELECT count(*) FROM hospital.medications WHERE code LIKE 'DEMO-MED-%' AND is_active) <> 2 THEN
        RAISE EXCEPTION 'Expected two synthetic DEMO medication catalogue entries';
    END IF;
    IF (SELECT count(*) FROM hospital.stock_balances sb
        JOIN hospital.inventory_locations l ON l.id=sb.location_id
        JOIN hospital.medication_batches b ON b.id=sb.batch_id
        JOIN hospital.medications m ON m.id=b.medication_id
        WHERE l.code='DEMO-PHARMACY-01' AND m.code LIKE 'DEMO-MED-%') <> 3 THEN
        RAISE EXCEPTION 'Expected three synthetic pharmacy stock balance fixtures';
    END IF;
    IF (SELECT count(*) FROM hospital.stock_movements WHERE reference_type='DEMO_SEED' AND reason='Synthetic opening balance') <> 3 THEN
        RAISE EXCEPTION 'Expected three synthetic opening stock ledger movements';
    END IF;
END $$;
SELECT 'Synthetic pharmacy fixtures validated; no clinical-grade medicine catalogue is included.' AS validation_result;
