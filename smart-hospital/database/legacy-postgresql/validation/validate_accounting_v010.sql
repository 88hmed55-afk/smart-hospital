DO $$
DECLARE
    v_active_branches BIGINT;
    v_accounted_branches BIGINT;
    v_missing_account_branches BIGINT;
    v_missing_current_period_branches BIGINT;
    v_missing_next_period_branches BIGINT;
    v_trigger_count BIGINT;
    v_post_trigger_count BIGINT;
BEGIN
    SELECT count(*) INTO v_active_branches
      FROM hospital.hospital_branches WHERE is_active = TRUE;
    SELECT count(DISTINCT branch_id) INTO v_accounted_branches
      FROM hospital.chart_of_accounts WHERE is_active = TRUE;
    SELECT count(*) INTO v_missing_account_branches
      FROM hospital.hospital_branches b
     WHERE b.is_active = TRUE
       AND (SELECT count(*) FROM hospital.chart_of_accounts a
             WHERE a.branch_id=b.id AND a.is_active=TRUE AND a.allows_posting=TRUE
               AND a.account_code IN ('1000','1010','1100','2200','4100','4200')) < 6;
    SELECT count(*) INTO v_missing_current_period_branches
      FROM hospital.hospital_branches b
     WHERE b.is_active = TRUE
       AND NOT EXISTS (SELECT 1 FROM hospital.fiscal_periods p
                        WHERE p.branch_id=b.id AND p.period_status='OPEN'
                          AND p.starts_on <= CURRENT_DATE AND p.ends_on > CURRENT_DATE);
    SELECT count(*) INTO v_missing_next_period_branches
      FROM hospital.hospital_branches b
     WHERE b.is_active = TRUE
       AND NOT EXISTS (SELECT 1 FROM hospital.fiscal_periods p
                        WHERE p.branch_id=b.id AND p.period_status='OPEN'
                          AND p.starts_on = make_date(EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER + 1, 1, 1));
    SELECT count(*) INTO v_trigger_count
      FROM pg_trigger
     WHERE tgname='trg_validate_journal_line_account' AND NOT tgisinternal;
    SELECT count(*) INTO v_post_trigger_count
      FROM pg_trigger
     WHERE tgname='trg_validate_journal_accounts_before_post' AND NOT tgisinternal;

    IF v_active_branches = 0 THEN
        RAISE EXCEPTION 'No active hospital branch exists; accounting seed cannot be validated.';
    END IF;
    IF v_accounted_branches < v_active_branches THEN
        RAISE EXCEPTION 'At least one active branch has no active chart of accounts.';
    END IF;
    IF v_missing_account_branches > 0 THEN
        RAISE EXCEPTION 'At least one active branch is missing one or more required default posting accounts.';
    END IF;
    IF v_missing_current_period_branches > 0 THEN
        RAISE EXCEPTION 'At least one active branch has no open fiscal period covering today.';
    END IF;
    IF v_missing_next_period_branches > 0 THEN
        RAISE EXCEPTION 'At least one active branch has no open fiscal period for the next calendar year.';
    END IF;
    IF v_trigger_count <> 1 OR v_post_trigger_count <> 1 THEN
        RAISE EXCEPTION 'One or more accounting posting guard triggers are missing.';
    END IF;
END $$;
