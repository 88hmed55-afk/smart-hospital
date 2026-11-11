-- SMART HOSPITAL MySQL-specific generated columns and uniqueness semantics.
-- These replace PostgreSQL partial unique indexes and GiST exclusion constraints.
USE hospital;

-- Only one default branch per user. Non-default rows carry NULL in this key.
ALTER TABLE user_branch_access
  ADD COLUMN default_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN is_default=1 THEN user_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_user_one_default_branch (default_user_id);

-- Only one primary diagnosis per encounter.
ALTER TABLE encounter_diagnoses
  ADD COLUMN primary_encounter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN is_primary=1 THEN encounter_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_encounter_one_primary_diagnosis (primary_encounter_id);

-- One global setting per key, while branch overrides remain unique per branch.
ALTER TABLE system_settings
  ADD COLUMN global_setting_key VARCHAR(100)
    GENERATED ALWAYS AS (CASE WHEN branch_id IS NULL THEN setting_key ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_system_setting_global (global_setting_key);

-- Active care-team membership and one active primary nurse per encounter.
ALTER TABLE encounter_care_team
  ADD COLUMN active_encounter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN encounter_id ELSE NULL END) STORED,
  ADD COLUMN active_staff_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN staff_id ELSE NULL END) STORED,
  ADD COLUMN active_primary_encounter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN released_at IS NULL AND care_role='PRIMARY_NURSE' THEN encounter_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_encounter_active_care_member (active_encounter_id, active_staff_id),
  ADD UNIQUE KEY uq_encounter_active_primary_nurse (active_primary_encounter_id);

-- Only one active admission per patient and branch.
ALTER TABLE admissions
  ADD COLUMN active_branch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN admission_status='ADMITTED' THEN branch_id ELSE NULL END) STORED,
  ADD COLUMN active_patient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN admission_status='ADMITTED' THEN patient_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_one_active_admission_per_patient_branch (active_branch_id, active_patient_id);

-- One active bed assignment per admission. Bed/time overlap is serialized by the service locking the bed row.
ALTER TABLE bed_assignments
  ADD COLUMN active_admission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN assignment_status='ACTIVE' THEN admission_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_admission_one_current_bed (active_admission_id);

-- One metric snapshot per metric/branch/period/department, including the NULL department scope.
ALTER TABLE metric_snapshots
  ADD COLUMN department_scope_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (COALESCE(department_id,'00000000-0000-0000-0000-000000000000')) STORED,
  ADD UNIQUE KEY uq_metric_snapshot_scope_period
    (metric_definition_id, branch_id, department_scope_id, period_granularity, window_start, window_end);

-- Only one dispense stock movement per dispense and batch, leaving other movement types unaffected.
ALTER TABLE stock_movements
  ADD COLUMN dispense_reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN reference_type='DISPENSE' AND movement_type='DISPENSE' THEN reference_id ELSE NULL END) STORED,
  ADD COLUMN dispense_batch_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN reference_type='DISPENSE' AND movement_type='DISPENSE' THEN batch_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_dispense_stock_movement (dispense_reference_id, dispense_batch_id);

-- Preserve the PostgreSQL partial uniqueness rule for originating journal records only.
ALTER TABLE journal_entries
  ADD COLUMN unique_source_module VARCHAR(32)
    GENERATED ALWAYS AS (CASE WHEN source_module IS NOT NULL AND source_reference_id IS NOT NULL AND reversal_of_entry_id IS NULL THEN source_module ELSE NULL END) STORED,
  ADD COLUMN unique_source_reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN source_module IS NOT NULL AND source_reference_id IS NOT NULL AND reversal_of_entry_id IS NULL THEN source_reference_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_journal_source_once (branch_id, unique_source_module, unique_source_reference_id);

-- Username comparisons are case-insensitive using the database collation.
ALTER TABLE user_accounts
  MODIFY username VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;

-- A normal unique key already enforces branch-scoped setting uniqueness.
-- All timestamps are UTC DATETIME(6); application converts to branch display timezone.

-- One evidence row per card and metric code; allows idempotent INSERT IGNORE from the analytics service.
ALTER TABLE decision_card_evidence
  ADD UNIQUE KEY uq_decision_card_evidence_code (decision_card_id, evidence_code);
