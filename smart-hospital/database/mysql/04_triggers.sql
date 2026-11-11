-- SMART HOSPITAL MySQL triggers. Run after 01_schema.sql and 03_constraints.sql.
USE hospital;
DELIMITER $$

CREATE TRIGGER bi_admissions_uuid BEFORE INSERT ON admissions
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_appointments_uuid BEFORE INSERT ON appointments
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_audit_logs_uuid BEFORE INSERT ON audit_logs
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_bed_assignments_uuid BEFORE INSERT ON bed_assignments
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_beds_uuid BEFORE INSERT ON beds
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_chart_of_accounts_uuid BEFORE INSERT ON chart_of_accounts
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_clinical_notes_uuid BEFORE INSERT ON clinical_notes
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_decision_card_actions_uuid BEFORE INSERT ON decision_card_actions
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_decision_card_evidence_uuid BEFORE INSERT ON decision_card_evidence
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_decision_cards_uuid BEFORE INSERT ON decision_cards
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_departments_uuid BEFORE INSERT ON departments
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_diagnosis_catalog_uuid BEFORE INSERT ON diagnosis_catalog
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_dispense_items_uuid BEFORE INSERT ON dispense_items
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_dispenses_uuid BEFORE INSERT ON dispenses
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_encounter_care_team_uuid BEFORE INSERT ON encounter_care_team
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_encounter_diagnoses_uuid BEFORE INSERT ON encounter_diagnoses
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_encounters_uuid BEFORE INSERT ON encounters
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_fiscal_periods_uuid BEFORE INSERT ON fiscal_periods
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_goods_receipt_items_uuid BEFORE INSERT ON goods_receipt_items
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_goods_receipts_uuid BEFORE INSERT ON goods_receipts
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_hospital_branches_uuid BEFORE INSERT ON hospital_branches
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_inventory_locations_uuid BEFORE INSERT ON inventory_locations
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_inventory_reorder_policies_uuid BEFORE INSERT ON inventory_reorder_policies
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_invoice_items_uuid BEFORE INSERT ON invoice_items
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_invoices_uuid BEFORE INSERT ON invoices
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_journal_entries_uuid BEFORE INSERT ON journal_entries
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_journal_lines_uuid BEFORE INSERT ON journal_lines
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_lab_order_items_uuid BEFORE INSERT ON lab_order_items
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_lab_orders_uuid BEFORE INSERT ON lab_orders
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_lab_results_uuid BEFORE INSERT ON lab_results
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_lab_test_catalog_uuid BEFORE INSERT ON lab_test_catalog
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_medication_batches_uuid BEFORE INSERT ON medication_batches
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_medications_uuid BEFORE INSERT ON medications
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_message_outbox_uuid BEFORE INSERT ON message_outbox
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_metric_definitions_uuid BEFORE INSERT ON metric_definitions
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_metric_snapshots_uuid BEFORE INSERT ON metric_snapshots
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_notifications_uuid BEFORE INSERT ON notifications
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_operational_alerts_uuid BEFORE INSERT ON operational_alerts
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_patient_allergies_uuid BEFORE INSERT ON patient_allergies
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_patient_branch_access_uuid BEFORE INSERT ON patient_branch_access
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_patient_contacts_uuid BEFORE INSERT ON patient_contacts
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_patients_uuid BEFORE INSERT ON patients
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_payments_uuid BEFORE INSERT ON payments
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_permissions_uuid BEFORE INSERT ON permissions
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_prescription_items_uuid BEFORE INSERT ON prescription_items
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_prescriptions_uuid BEFORE INSERT ON prescriptions
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_purchase_order_approvals_uuid BEFORE INSERT ON purchase_order_approvals
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_purchase_order_items_uuid BEFORE INSERT ON purchase_order_items
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_purchase_orders_uuid BEFORE INSERT ON purchase_orders
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_roles_uuid BEFORE INSERT ON roles
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_rooms_uuid BEFORE INSERT ON rooms
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_service_catalog_uuid BEFORE INSERT ON service_catalog
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_staff_members_uuid BEFORE INSERT ON staff_members
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_stock_balances_uuid BEFORE INSERT ON stock_balances
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_stock_movements_uuid BEFORE INSERT ON stock_movements
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_suppliers_uuid BEFORE INSERT ON suppliers
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_system_settings_uuid BEFORE INSERT ON system_settings
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_user_accounts_uuid BEFORE INSERT ON user_accounts
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_vital_signs_uuid BEFORE INSERT ON vital_signs
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bi_wards_uuid BEFORE INSERT ON wards
FOR EACH ROW
BEGIN
  IF NEW.id IS NULL OR NEW.id='' THEN SET NEW.id=UUID(); END IF;
END$$

CREATE TRIGGER bu_audit_logs_append_only BEFORE UPDATE ON audit_logs
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit log rows are append-only';
END$$
CREATE TRIGGER bd_audit_logs_append_only BEFORE DELETE ON audit_logs
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit log rows are append-only';
END$$

CREATE TRIGGER bu_vital_signs_append_only BEFORE UPDATE ON vital_signs
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Vital signs are append-only; record a correction instead';
END$$
CREATE TRIGGER bd_vital_signs_append_only BEFORE DELETE ON vital_signs
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Vital signs are append-only';
END$$

CREATE TRIGGER bu_payments_posted_immutable BEFORE UPDATE ON payments
FOR EACH ROW
BEGIN
  IF OLD.status='POSTED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted payments are immutable; use a reversal workflow';
  END IF;
END$$
CREATE TRIGGER bd_payments_posted_immutable BEFORE DELETE ON payments
FOR EACH ROW
BEGIN
  IF OLD.status='POSTED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted payments are immutable';
  END IF;
END$$

CREATE TRIGGER bu_dispenses_posted_immutable BEFORE UPDATE ON dispenses
FOR EACH ROW
BEGIN
  IF OLD.status='POSTED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted dispenses are immutable; use an approved reversal workflow';
  END IF;
END$$
CREATE TRIGGER bd_dispenses_immutable BEFORE DELETE ON dispenses
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dispense headers are retained as transaction history';
END$$

CREATE TRIGGER bu_journal_entries_posted_immutable BEFORE UPDATE ON journal_entries
FOR EACH ROW
BEGIN
  IF OLD.entry_status IN ('POSTED','VOID') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted or void journal entries are immutable';
  END IF;
END$$
CREATE TRIGGER bd_journal_entries_posted_immutable BEFORE DELETE ON journal_entries
FOR EACH ROW
BEGIN
  IF OLD.entry_status IN ('POSTED','VOID') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted or void journal entries cannot be deleted';
  END IF;
END$$

CREATE TRIGGER bu_journal_lines_posted_immutable BEFORE UPDATE ON journal_lines
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(12);
  SELECT entry_status INTO v_status FROM journal_entries WHERE id=OLD.journal_entry_id;
  IF v_status IN ('POSTED','VOID') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lines of posted or void journal entries are immutable';
  END IF;
END$$
CREATE TRIGGER bd_journal_lines_posted_immutable BEFORE DELETE ON journal_lines
FOR EACH ROW
BEGIN
  DECLARE v_status VARCHAR(12);
  SELECT entry_status INTO v_status FROM journal_entries WHERE id=OLD.journal_entry_id;
  IF v_status IN ('POSTED','VOID') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lines of posted or void journal entries cannot be deleted';
  END IF;
END$$

CREATE TRIGGER bu_clinical_notes_signed_immutable BEFORE UPDATE ON clinical_notes
FOR EACH ROW
BEGIN
  IF OLD.note_status='SIGNED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Signed clinical notes are immutable; create an amendment';
  END IF;
END$$
CREATE TRIGGER bd_clinical_notes_signed_immutable BEFORE DELETE ON clinical_notes
FOR EACH ROW
BEGIN
  IF OLD.note_status='SIGNED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Signed clinical notes cannot be deleted';
  END IF;
END$$

CREATE TRIGGER bu_journal_entry_validate_post BEFORE UPDATE ON journal_entries
FOR EACH ROW
BEGIN
  DECLARE v_count BIGINT DEFAULT 0;
  DECLARE v_debit DECIMAL(20,2) DEFAULT 0;
  DECLARE v_credit DECIMAL(20,2) DEFAULT 0;
  DECLARE v_period_status VARCHAR(12);
  IF OLD.entry_status='DRAFT' AND NEW.entry_status='POSTED' THEN
    SELECT COUNT(*), COALESCE(SUM(debit_amount),0), COALESCE(SUM(credit_amount),0)
      INTO v_count, v_debit, v_credit
      FROM journal_lines WHERE journal_entry_id=OLD.id;
    IF v_count < 2 OR v_debit <= 0 OR v_debit <> v_credit THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Journal entry must contain at least two balanced lines';
    END IF;
    SELECT period_status INTO v_period_status FROM fiscal_periods WHERE id=NEW.fiscal_period_id;
    IF v_period_status IS NULL OR v_period_status <> 'OPEN' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Journal entry fiscal period must be open';
    END IF;
    SET NEW.posted_at = COALESCE(NEW.posted_at, CURRENT_TIMESTAMP(6));
  END IF;
END$$
DELIMITER ;
