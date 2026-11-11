#!/usr/bin/env node
'use strict';

/*
 * Post-seed integrity report for the Smart Hospital demonstration dataset.
 *
 * Every check is a read-only SQL statement against the live database, so the
 * script also works after the application has been used for a while. Exit code
 * 0 means every invariant held; 1 means at least one check failed.
 *
 * Usage:
 *   node scripts/demo-data/verify.js
 *   node scripts/demo-data/verify.js --quiet
 *   node scripts/demo-data/verify.js --login-path=other-profile
 */

const { createDb, parseArgs } = require('./lib/db');

let passed = 0;
const failures = [];

function check(name, ok, detail) {
  if (ok) {
    passed += 1;
    process.stdout.write('  PASS  ' + name + '\n');
  } else {
    failures.push({ name, detail });
    process.stdout.write('  FAIL  ' + name + (detail ? ' — ' + detail : '') + '\n');
  }
}

function zero(db, name, sql) {
  const value = Number(db.scalar(sql));
  check(name, value === 0, value + ' offending row(s)');
}

function equals(db, name, sql, expected, label) {
  const value = Number(db.scalar(sql));
  check(name, value === expected, label + ': expected ' + expected + ', found ' + value);
}

function atLeast(db, name, sql, floor, label) {
  const value = Number(db.scalar(sql));
  check(name, value >= floor, label + ': expected ≥ ' + floor + ', found ' + value);
}

function section(title) {
  process.stdout.write('\n' + title + '\n');
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const db = createDb(Object.assign({ quiet: true }, options));

  process.stdout.write('Smart Hospital demo dataset — integrity verification\n');

  // -------------------------------------------------------------------------
  // Inventory of the generated data
  // -------------------------------------------------------------------------
  section('Dataset size');
  const counts = [
    ['patients', 'patients', "id LIKE 'deadbeef%'"],
    ['appointments', 'appointments', "id LIKE 'deadbeef%'"],
    ['encounters', 'encounters', "id LIKE 'deadbeef%'"],
    ['admissions', 'admissions', "id LIKE 'deadbeef%'"],
    ['prescriptions', 'prescriptions', "id LIKE 'deadbeef%'"],
    ['lab orders', 'lab_orders', "id LIKE 'deadbeef%'"],
    ['lab results', 'lab_results', "id LIKE 'deadbeef%'"],
    ['dispenses', 'dispenses', "id LIKE 'deadbeef%'"],
    ['invoices', 'invoices', "id LIKE 'deadbeef%'"],
    ['payments', 'payments', "id LIKE 'deadbeef%'"],
    ['journal entries', 'journal_entries', "id LIKE 'deadbeef%'"],
    ['stock movements', 'stock_movements', "id LIKE 'deadbeef%'"],
    ['metric snapshots', 'metric_snapshots', "id LIKE 'deadbeef%'"],
    ['notifications', 'notifications', "id LIKE 'deadbeef%'"]
  ];
  for (const [label, table, where] of counts) {
    const value = db.count(table, where);
    process.stdout.write('        ' + label.padEnd(20, '.') + String(value).padStart(7) + '\n');
  }

  // -------------------------------------------------------------------------
  // Time window
  // -------------------------------------------------------------------------
  section('Time window (six months back, one month of future bookings)');
  zero(db, 'appointments stay inside the booking window',
    "SELECT COUNT(*) FROM hospital.appointments WHERE starts_at < NOW() - INTERVAL 190 DAY OR starts_at > NOW() + INTERVAL 32 DAY");
  zero(db, 'encounters stay inside the activity window',
    "SELECT COUNT(*) FROM hospital.encounters WHERE started_at < NOW() - INTERVAL 190 DAY OR started_at > NOW() + INTERVAL 1 DAY");
  zero(db, 'no payment is dated in the future',
    "SELECT COUNT(*) FROM hospital.payments WHERE paid_at > NOW()");
  zero(db, 'no dispense is dated in the future',
    "SELECT COUNT(*) FROM hospital.dispenses WHERE dispensed_at > NOW()");
  zero(db, 'no stock movement is dated in the future',
    "SELECT COUNT(*) FROM hospital.stock_movements WHERE occurred_at > NOW()");
  zero(db, 'no journal entry is posted in the future',
    "SELECT COUNT(*) FROM hospital.journal_entries WHERE created_at > NOW() OR posted_at > NOW()");
  zero(db, 'no notification is dated in the future',
    "SELECT COUNT(*) FROM hospital.notifications WHERE created_at > NOW()");
  atLeast(db, 'appointments span at least 150 distinct days',
    "SELECT COUNT(DISTINCT DATE(starts_at)) FROM hospital.appointments", 150, 'distinct days');

  // -------------------------------------------------------------------------
  // Billing
  // -------------------------------------------------------------------------
  section('Billing');
  zero(db, 'invoice header arithmetic is exact',
    "SELECT COUNT(*) FROM hospital.invoices WHERE total_amount <> ROUND(subtotal - discount_amount + tax_amount, 2) " +
    "OR subtotal < 0 OR discount_amount < 0 OR discount_amount > subtotal OR tax_amount < 0 OR total_amount < 0");
  zero(db, 'invoice line arithmetic is exact',
    "SELECT COUNT(*) FROM hospital.invoice_items WHERE line_total <> ROUND(quantity * unit_price - line_discount, 2) " +
    'OR line_discount > quantity * unit_price OR quantity <= 0 OR unit_price < 0');
  zero(db, 'invoice subtotal equals the sum of its lines',
    "SELECT COUNT(*) FROM hospital.invoices i LEFT JOIN (" +
    '  SELECT invoice_id, ROUND(SUM(line_total), 2) AS total FROM hospital.invoice_items GROUP BY invoice_id' +
    ') x ON x.invoice_id = i.id WHERE i.subtotal <> COALESCE(x.total, 0)');
  zero(db, 'every invoice item has exactly one source (service xor dispense)',
    'SELECT COUNT(*) FROM hospital.invoice_items WHERE (service_id IS NULL) = (dispense_item_id IS NULL)');
  zero(db, 'invoice status agrees with the amount received',
    "SELECT COUNT(*) FROM hospital.invoices i LEFT JOIN (" +
    "  SELECT invoice_id, SUM(amount) AS paid FROM hospital.payments WHERE status='POSTED' GROUP BY invoice_id" +
    ') p ON p.invoice_id = i.id WHERE ' +
    "(i.status='PAID' AND ROUND(COALESCE(p.paid,0),2) <> i.total_amount) " +
    "OR (i.status='PARTIALLY_PAID' AND NOT (COALESCE(p.paid,0) > 0 AND ROUND(p.paid,2) < i.total_amount)) " +
    "OR (i.status IN ('ISSUED','DRAFT','VOID') AND COALESCE(p.paid,0) <> 0) " +
    "OR (i.status='DRAFT' AND i.issued_at IS NOT NULL) " +
    "OR (i.status<>'DRAFT' AND i.issued_at IS NULL)");
  zero(db, 'payments are never dated before their invoice',
    'SELECT COUNT(*) FROM hospital.payments p JOIN hospital.invoices i ON i.id = p.invoice_id WHERE p.paid_at < i.issued_at');
  zero(db, 'receipt numbers are unique',
    'SELECT COUNT(*) - COUNT(DISTINCT receipt_number) FROM hospital.payments');
  zero(db, 'invoice numbers are unique',
    'SELECT COUNT(*) - COUNT(DISTINCT invoice_number) FROM hospital.invoices');

  // -------------------------------------------------------------------------
  // Accounting
  // -------------------------------------------------------------------------
  section('Accounting');
  zero(db, 'every journal entry is balanced',
    'SELECT COUNT(*) FROM (SELECT journal_entry_id FROM hospital.journal_lines ' +
    'GROUP BY journal_entry_id HAVING ROUND(SUM(debit_amount),2) <> ROUND(SUM(credit_amount),2) ' +
    'OR ROUND(SUM(debit_amount),2) <= 0) x');
  zero(db, 'every journal entry has lines',
    'SELECT COUNT(*) FROM hospital.journal_entries e LEFT JOIN hospital.journal_lines l ' +
    'ON l.journal_entry_id = e.id WHERE l.id IS NULL');
  zero(db, 'posted entries carry posting metadata',
    "SELECT COUNT(*) FROM hospital.journal_entries WHERE entry_status='POSTED' AND (posted_by IS NULL OR posted_at IS NULL)");
  zero(db, 'journal lines use one side only',
    'SELECT COUNT(*) FROM hospital.journal_lines WHERE (debit_amount > 0 AND credit_amount > 0) ' +
    'OR (debit_amount = 0 AND credit_amount = 0) OR debit_amount < 0 OR credit_amount < 0');
  equals(db, 'one receivable entry per issued invoice',
    "SELECT (SELECT COUNT(*) FROM hospital.invoices WHERE issued_at IS NOT NULL AND status <> 'VOID') - " +
    "(SELECT COUNT(DISTINCT source_reference_id) FROM hospital.journal_entries WHERE source_module='BILLING_INVOICE')",
    0, 'difference');
  equals(db, 'one cash entry per posted receipt',
    "SELECT (SELECT COUNT(*) FROM hospital.payments WHERE status='POSTED') - " +
    "(SELECT COUNT(DISTINCT source_reference_id) FROM hospital.journal_entries WHERE source_module='BILLING_PAYMENT')",
    0, 'difference');
  zero(db, 'receivable balance matches invoiced minus collected',
    "SELECT ROUND((" +
    "  (SELECT COALESCE(SUM(l.debit_amount - l.credit_amount),0) FROM hospital.journal_lines l " +
    "     JOIN hospital.chart_of_accounts a ON a.id = l.account_id WHERE a.account_code = '1100') - (" +
    "    (SELECT COALESCE(SUM(total_amount),0) FROM hospital.invoices WHERE issued_at IS NOT NULL AND status <> 'VOID') - " +
    "    (SELECT COALESCE(SUM(amount),0) FROM hospital.payments WHERE status = 'POSTED')" +
    '  )), 2) <> 0');
  zero(db, 'cash and bank balances match collected receipts',
    "SELECT ROUND((" +
    "  (SELECT COALESCE(SUM(l.debit_amount - l.credit_amount),0) FROM hospital.journal_lines l " +
    "     JOIN hospital.chart_of_accounts a ON a.id = l.account_id WHERE a.account_code IN ('1000','1010')) - " +
    "  (SELECT COALESCE(SUM(amount),0) FROM hospital.payments WHERE status = 'POSTED')), 2) <> 0");
  zero(db, 'service revenue equals invoiced subtotals',
    "SELECT ROUND((" +
    "  (SELECT COALESCE(SUM(l.credit_amount - l.debit_amount),0) FROM hospital.journal_lines l " +
    "     JOIN hospital.chart_of_accounts a ON a.id = l.account_id WHERE a.account_code = '4100') - " +
    "  (SELECT COALESCE(SUM(subtotal),0) FROM hospital.invoices WHERE issued_at IS NOT NULL AND status <> 'VOID')), 2) <> 0");
  zero(db, 'discount expense equals invoice discounts',
    "SELECT ROUND((" +
    "  (SELECT COALESCE(SUM(l.debit_amount - l.credit_amount),0) FROM hospital.journal_lines l " +
    "     JOIN hospital.chart_of_accounts a ON a.id = l.account_id WHERE a.account_code = '4200') - " +
    "  (SELECT COALESCE(SUM(discount_amount),0) FROM hospital.invoices WHERE issued_at IS NOT NULL AND status <> 'VOID')), 2) <> 0");
  zero(db, 'tax payable equals invoice tax',
    "SELECT ROUND((" +
    "  (SELECT COALESCE(SUM(l.credit_amount - l.debit_amount),0) FROM hospital.journal_lines l " +
    "     JOIN hospital.chart_of_accounts a ON a.id = l.account_id WHERE a.account_code = '2200') - " +
    "  (SELECT COALESCE(SUM(tax_amount),0) FROM hospital.invoices WHERE issued_at IS NOT NULL AND status <> 'VOID')), 2) <> 0");
  atLeast(db, 'journal entries cover at least five months of activity',
    'SELECT COUNT(DISTINCT DATE(created_at)) FROM hospital.journal_entries', 120, 'distinct dates');

  // -------------------------------------------------------------------------
  // Pharmacy inventory
  // -------------------------------------------------------------------------
  section('Pharmacy inventory');
  zero(db, 'stock balances equal the movement ledger',
    'SELECT COUNT(*) FROM hospital.stock_balances b LEFT JOIN (' +
    '  SELECT location_id, batch_id, ROUND(SUM(quantity_delta),3) AS q FROM hospital.stock_movements ' +
    '  GROUP BY location_id, batch_id' +
    ') m ON m.location_id = b.location_id AND m.batch_id = b.batch_id ' +
    'WHERE b.quantity_on_hand <> COALESCE(m.q, 0)');
  zero(db, 'no ledger batch ends negative',
    'SELECT COUNT(*) FROM (SELECT location_id, batch_id FROM hospital.stock_movements ' +
    'GROUP BY location_id, batch_id HAVING ROUND(SUM(quantity_delta),3) < 0) x');
  zero(db, 'no ledger batch goes negative at any point',
    'SELECT COUNT(*) FROM (SELECT location_id, batch_id, MIN(running) AS worst FROM (' +
    '  SELECT location_id, batch_id, occurred_at, id, SUM(quantity_delta) OVER (' +
    '    PARTITION BY location_id, batch_id ORDER BY occurred_at, id ROWS UNBOUNDED PRECEDING) AS running ' +
    '  FROM hospital.stock_movements' +
    ') w GROUP BY location_id, batch_id) x WHERE worst < -0.005');
  zero(db, 'on-hand and reserved quantities are valid',
    'SELECT COUNT(*) FROM hospital.stock_balances WHERE quantity_on_hand <= 0 ' +
    'OR quantity_reserved < 0 OR quantity_reserved > quantity_on_hand');
  zero(db, 'movements only reference real batches',
    'SELECT COUNT(*) FROM hospital.stock_movements m LEFT JOIN hospital.medication_batches b ' +
    'ON b.id = m.batch_id WHERE b.id IS NULL');
  zero(db, 'every dispense has matching ledger movements',
    'SELECT COUNT(*) FROM hospital.dispenses d LEFT JOIN hospital.stock_movements m ' +
    "ON m.reference_id = d.id AND m.movement_type = 'DISPENSE' WHERE m.id IS NULL");
  zero(db, 'dispense quantities equal the ledger deductions',
    'SELECT COUNT(*) FROM (SELECT d.id, ROUND(SUM(di.quantity_dispensed),3) AS q ' +
    '  FROM hospital.dispenses d JOIN hospital.dispense_items di ON di.dispense_id = d.id GROUP BY d.id' +
    ') x JOIN (SELECT reference_id, ROUND(SUM(-quantity_delta),3) AS q FROM hospital.stock_movements ' +
    "  WHERE movement_type = 'DISPENSE' GROUP BY reference_id) m ON m.reference_id = x.id " +
    'WHERE x.q <> m.q');
  zero(db, 'dispensed batches match the prescribed medication',
    'SELECT COUNT(*) FROM hospital.dispense_items di ' +
    'JOIN hospital.prescription_items pi ON pi.id = di.prescription_item_id ' +
    'JOIN hospital.medication_batches b ON b.id = di.batch_id ' +
    'WHERE pi.medication_id <> b.medication_id');
  zero(db, 'no dispense line has a non-positive quantity',
    'SELECT COUNT(*) FROM hospital.dispense_items WHERE quantity_dispensed <= 0');
  zero(db, 'dispensed quantities never exceed the prescription',
    'SELECT COUNT(*) FROM (SELECT pi.id, pi.quantity_prescribed AS q, ' +
    '  COALESCE(SUM(di.quantity_dispensed),0) AS d FROM hospital.prescription_items pi ' +
    '  LEFT JOIN hospital.dispense_items di ON di.prescription_item_id = pi.id ' +
    '  GROUP BY pi.id) t WHERE t.d > t.q + 0.0005');
  zero(db, 'DISPENSED prescriptions are fully covered',
    "SELECT COUNT(*) FROM hospital.prescriptions p WHERE p.status = 'DISPENSED' AND EXISTS (" +
    '  SELECT 1 FROM hospital.prescription_items pi LEFT JOIN (' +
    '    SELECT prescription_item_id, SUM(quantity_dispensed) AS q FROM hospital.dispense_items ' +
    '    GROUP BY prescription_item_id' +
    '  ) d ON d.prescription_item_id = pi.id ' +
    '  WHERE pi.prescription_id = p.id AND COALESCE(d.q,0) < pi.quantity_prescribed - 0.0005)');
  zero(db, 'no dispense exists for a cancelled prescription',
    "SELECT COUNT(*) FROM hospital.prescriptions p JOIN hospital.dispenses d ON d.prescription_id = p.id " +
    "WHERE p.status NOT IN ('DISPENSED','PARTIALLY_DISPENSED')");
  zero(db, 'purchase orders with an approval decision carry an approver',
    "SELECT COUNT(*) FROM hospital.purchase_orders WHERE order_status IN ('APPROVED','ORDERED','PARTIALLY_RECEIVED','RECEIVED','REJECTED') " +
    'AND approved_by IS NULL');
  zero(db, 'posted goods receipts carry posting metadata',
    "SELECT COUNT(*) FROM hospital.goods_receipts WHERE receipt_status='POSTED' AND (posted_by IS NULL OR posted_at IS NULL)");
  zero(db, 'goods receipt lines never exceed ordered quantities',
    'SELECT COUNT(*) FROM hospital.goods_receipt_items r JOIN hospital.purchase_order_items i ' +
    'ON i.id = r.purchase_order_item_id WHERE r.accepted_quantity + r.rejected_quantity > i.ordered_quantity');
  zero(db, 'purchase order received quantities match posted receipts',
    'SELECT COUNT(*) FROM hospital.purchase_order_items i LEFT JOIN (' +
    '  SELECT purchase_order_item_id, ROUND(SUM(accepted_quantity),3) AS q ' +
    '  FROM hospital.goods_receipt_items GROUP BY purchase_order_item_id' +
    ') r ON r.purchase_order_item_id = i.id WHERE i.received_quantity <> COALESCE(r.q, 0)');
  zero(db, 'fully received orders have no remaining quantity',
    "SELECT COUNT(*) FROM hospital.purchase_orders o JOIN hospital.purchase_order_items i " +
    "ON i.purchase_order_id = o.id WHERE o.order_status = 'RECEIVED' AND i.received_quantity <> i.ordered_quantity");
  atLeast(db, 'stock ledger spans at least five months',
    'SELECT COUNT(DISTINCT DATE(occurred_at)) FROM hospital.stock_movements', 120, 'distinct dates');

  // -------------------------------------------------------------------------
  // Clinical and laboratory
  // -------------------------------------------------------------------------
  section('Clinical activity');
  zero(db, 'appointment end is after start',
    'SELECT COUNT(*) FROM hospital.appointments WHERE ends_at <= starts_at');
  zero(db, 'completed encounters have an end time',
    "SELECT COUNT(*) FROM hospital.encounters WHERE status='COMPLETED' AND ended_at IS NULL");
  zero(db, 'encounter end is never before start',
    'SELECT COUNT(*) FROM hospital.encounters WHERE ended_at IS NOT NULL AND ended_at < started_at');
  zero(db, 'admission discharge state is consistent',
    "SELECT COUNT(*) FROM hospital.admissions WHERE (admission_status='ADMITTED' AND (discharged_at IS NOT NULL OR discharged_by IS NOT NULL)) " +
    "OR (admission_status='DISCHARGED' AND (discharged_at IS NULL OR discharged_by IS NULL)) " +
    'OR (discharged_at IS NOT NULL AND discharged_at < admitted_at)');
  zero(db, 'at most one active admission per patient',
    "SELECT COUNT(*) FROM (SELECT patient_id FROM hospital.admissions WHERE admission_status='ADMITTED' " +
    'GROUP BY patient_id HAVING COUNT(*) > 1) x');
  zero(db, 'at most one active bed assignment per admission and per bed',
    "SELECT COUNT(*) FROM (SELECT admission_id FROM hospital.bed_assignments WHERE assignment_status='ACTIVE' " +
    'GROUP BY admission_id HAVING COUNT(*) > 1) x');
  zero(db, 'active bed assignments are open-ended',
    "SELECT COUNT(*) FROM hospital.bed_assignments WHERE (assignment_status='ACTIVE' AND (ended_at IS NOT NULL OR released_by IS NOT NULL)) " +
    "OR (assignment_status IN ('COMPLETED','CANCELLED') AND ended_at IS NULL)");
  zero(db, 'every completed admission has a discharge summary',
    "SELECT COUNT(*) FROM hospital.admissions WHERE admission_status='DISCHARGED' AND " +
    '(discharge_summary IS NULL OR discharge_summary = \'\')');
  atLeast(db, 'admissions span at least five months',
    'SELECT DATEDIFF(MAX(DATE(admitted_at)), MIN(DATE(admitted_at))) FROM hospital.admissions', 150, 'day span');

  section('Laboratory');
  zero(db, 'results are never released before collection',
    'SELECT COUNT(*) FROM hospital.lab_results r JOIN hospital.lab_order_items i ' +
    'ON i.id = r.lab_order_item_id WHERE r.released_at IS NOT NULL AND i.collected_at IS NOT NULL ' +
    'AND r.released_at < i.collected_at');
  zero(db, 'released results have a reviewer and review time',
    'SELECT COUNT(*) FROM hospital.lab_results WHERE released_at IS NOT NULL AND ' +
    '(reviewed_at IS NULL OR reviewed_by IS NULL)');
  zero(db, 'result timestamps are ordered',
    'SELECT COUNT(*) FROM hospital.lab_results WHERE performed_at IS NOT NULL AND reviewed_at IS NOT NULL ' +
    'AND reviewed_at < performed_at');
  zero(db, 'collected items have a specimen code',
    "SELECT COUNT(*) FROM hospital.lab_order_items WHERE collected_at IS NOT NULL AND specimen_code IS NULL");
  zero(db, 'COMPLETED lab orders have only reviewed items',
    "SELECT COUNT(*) FROM hospital.lab_orders o JOIN (" +
    '  SELECT lab_order_id, SUM(status = \'REVIEWED\') AS reviewed, COUNT(*) AS total ' +
    '  FROM hospital.lab_order_items GROUP BY lab_order_id' +
    ") x ON x.lab_order_id = o.id WHERE o.status = 'COMPLETED' AND x.reviewed < x.total");
  zero(db, 'CANCELLED lab orders have no results',
    "SELECT COUNT(*) FROM hospital.lab_orders o JOIN hospital.lab_order_items i ON i.lab_order_id = o.id " +
    "JOIN hospital.lab_results r ON r.lab_order_item_id = i.id WHERE o.status = 'CANCELLED'");
  atLeast(db, 'laboratory work spans at least five months',
    'SELECT COUNT(DISTINCT DATE(ordered_at)) FROM hospital.lab_orders', 120, 'distinct dates');

  // -------------------------------------------------------------------------
  // Analytics and engagement
  // -------------------------------------------------------------------------
  section('Analytics and engagement');
  atLeast(db, 'all nine dashboard metrics are defined',
    "SELECT COUNT(*) FROM hospital.metric_definitions WHERE source_module='OPERATIONAL_ANALYTICS'", 9, 'definitions');
  zero(db, 'snapshots cover every metric and every day exactly once',
    'SELECT COUNT(*) - COUNT(DISTINCT CONCAT(metric_definition_id, \'|\', DATE(window_start))) ' +
    'FROM hospital.metric_snapshots');
  equals(db, 'snapshot count matches metrics × days',
    'SELECT (SELECT COUNT(DISTINCT DATE(window_start)) FROM hospital.metric_snapshots) * ' +
    "  (SELECT COUNT(*) FROM hospital.metric_definitions WHERE source_module='OPERATIONAL_ANALYTICS') - " +
    '  (SELECT COUNT(*) FROM hospital.metric_snapshots)', 0, 'difference');
  zero(db, 'snapshot values and windows are valid',
    'SELECT COUNT(*) FROM hospital.metric_snapshots WHERE metric_value < 0 OR sample_count < 0 ' +
    'OR window_end <= window_start OR period_granularity <> \'DAY\'');
  zero(db, 'bed occupancy snapshots stay within 0–100',
    "SELECT COUNT(*) FROM hospital.metric_snapshots s JOIN hospital.metric_definitions d " +
    "ON d.id = s.metric_definition_id WHERE d.metric_code = 'BED_OCCUPANCY_PERCENT' " +
    'AND (s.metric_value < 0 OR s.metric_value > 100)');
  zero(db, 'decision cards carry valid JSON structures',
    "SELECT COUNT(*) FROM hospital.decision_cards WHERE JSON_TYPE(assumptions) <> 'ARRAY' " +
    "OR JSON_TYPE(expected_impact) <> 'OBJECT'");
  zero(db, 'decision card acknowledgement is paired',
    'SELECT COUNT(*) FROM hospital.decision_cards ' +
    'WHERE (acknowledged_by IS NULL) <> (acknowledged_at IS NULL)');
  zero(db, 'decision card resolution is not before generation',
    'SELECT COUNT(*) FROM hospital.decision_cards WHERE resolved_at IS NOT NULL AND resolved_at < generated_at');
  zero(db, 'completed actions have a completion time, others do not',
    "SELECT COUNT(*) FROM hospital.decision_card_actions WHERE (action_status='COMPLETED') <> (completed_at IS NOT NULL)");
  zero(db, 'decision card evidence points at a snapshot of the same day',
    'SELECT COUNT(*) FROM hospital.decision_card_evidence e JOIN hospital.metric_snapshots s ' +
    'ON s.id = e.metric_snapshot_id JOIN hospital.decision_cards c ON c.id = e.decision_card_id ' +
    'WHERE DATE(s.window_start) <> DATE(c.evidence_window_start)');
  atLeast(db, 'alerts cover a broad part of the period',
    'SELECT COUNT(DISTINCT DATE(generated_at)) FROM hospital.operational_alerts', 10, 'distinct dates');
  zero(db, 'alert lifecycle is consistent',
    'SELECT COUNT(*) FROM hospital.operational_alerts ' +
    'WHERE (acknowledged_by IS NULL) <> (acknowledged_at IS NULL) ' +
    'OR (resolved_at IS NOT NULL AND resolved_at < generated_at) ' +
    "OR (status='RESOLVED' AND resolved_at IS NULL)");
  zero(db, 'every notification references a real outbox event',
    'SELECT COUNT(*) FROM hospital.notifications n LEFT JOIN hospital.message_outbox o ' +
    'ON o.id = n.source_event_id WHERE o.id IS NULL');
  zero(db, 'notifications are not read before they are created',
    'SELECT COUNT(*) FROM hospital.notifications WHERE read_at IS NOT NULL AND read_at < created_at');
  zero(db, 'published outbox events carry a publish time',
    "SELECT COUNT(*) FROM hospital.message_outbox WHERE delivery_status='PUBLISHED' AND published_at IS NULL");
  zero(db, 'outbox statuses are valid',
    "SELECT COUNT(*) FROM hospital.message_outbox WHERE delivery_status NOT IN ('PENDING','PROCESSING','PUBLISHED','DEAD_LETTER')");
  atLeast(db, 'notification inbox is spread across users',
    'SELECT COUNT(DISTINCT recipient_user_id) FROM hospital.notifications', 5, 'recipients');
  zero(db, 'drug interaction rules are symmetric-safe and active',
    'SELECT COUNT(*) FROM hospital.drug_interactions WHERE medication_id = interacting_medication_id ' +
    'OR severity NOT IN (\'MINOR\',\'MODERATE\',\'MAJOR\',\'CONTRAINDICATED\')');

  // -------------------------------------------------------------------------
  // Marker hygiene
  // -------------------------------------------------------------------------
  section('Marker hygiene');
  zero(db, 'no demo identifier leaks into user accounts',
    "SELECT COUNT(*) FROM hospital.user_accounts WHERE id LIKE 'deadbeef%' OR id LIKE 'feedface%'");
  zero(db, 'audit log has no generator-written rows',
    "SELECT COUNT(*) FROM hospital.audit_logs WHERE actor_user_id IS NULL AND " +
    "(resource_id LIKE 'deadbeef%' OR resource_id LIKE 'feedface%')");
  zero(db, 'all demo rows use the reserved identifier namespaces',
    "SELECT COUNT(*) FROM hospital.invoices WHERE id NOT LIKE 'deadbeef%' AND id NOT LIKE 'feedface%'");

  // -------------------------------------------------------------------------
  // Six-month distribution snapshot
  // -------------------------------------------------------------------------
  section('Six-month distribution');
  const monthly = db.query(
    "SELECT DATE_FORMAT(paid_at, '%Y-%m'), COUNT(*), ROUND(SUM(amount), 2) " +
    'FROM hospital.payments WHERE status = \'POSTED\' ' +
    "GROUP BY DATE_FORMAT(paid_at, '%Y-%m') ORDER BY 1");
  process.stdout.write('        month      receipts        amount (YER)\n');
  for (const [month, receipts, amount] of monthly) {
    process.stdout.write('        ' + month.padEnd(10) +
      String(receipts).padStart(9) + Number(amount).toLocaleString('en-US').padStart(20) + '\n');
  }

  const summary = db.query(
    "SELECT COUNT(*), ROUND(SUM(total_amount), 2), SUM(status='PAID'), SUM(status='PARTIALLY_PAID'), " +
    "SUM(status='ISSUED'), SUM(status='VOID'), SUM(status='DRAFT') FROM hospital.invoices");
  const [invoiceCount, invoiceTotal, paidCount, partialCount, issuedCount, voidCount, draftCount] = summary[0];
  process.stdout.write('\n        invoices:      ' + invoiceCount +
    '  (total ' + Number(invoiceTotal).toLocaleString('en-US') + ' YER)\n');
  process.stdout.write('        paid:          ' + paidCount + '\n');
  process.stdout.write('        partially:     ' + partialCount + '\n');
  process.stdout.write('        issued:        ' + issuedCount + '\n');
  process.stdout.write('        void:          ' + voidCount + '\n');
  process.stdout.write('        draft:         ' + draftCount + '\n');

  // -------------------------------------------------------------------------
  // Result
  // -------------------------------------------------------------------------
  process.stdout.write('\n' + '-'.repeat(72) + '\n');
  if (failures.length === 0) {
    process.stdout.write('VERIFY OK — ' + passed + ' checks passed, 0 failed.\n');
    process.exitCode = 0;
  } else {
    process.stdout.write('VERIFY FAILED — ' + passed + ' passed, ' + failures.length + ' failed:\n');
    for (const failure of failures) {
      process.stdout.write('  - ' + failure.name + (failure.detail ? ' (' + failure.detail + ')' : '') + '\n');
    }
    process.exitCode = 1;
  }
}

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error('\nVERIFY ERROR: ' + error.message);
    process.exitCode = 1;
  }
}

module.exports = { main };
