#!/usr/bin/env node
'use strict';

/*
 * Smart Hospital demonstration dataset (six months).
 *
 * The generator is intentionally non-destructive: it only inserts rows, never
 * updates or deletes existing records, and it keeps its own identifier
 * namespaces so every row it creates can be recognised later:
 *
 *   deadbeef-0000-4000-8000-xxxxxxxxxxxx  transactional rows
 *   feedface-0000-4000-8000-xxxxxxxxxxxx  reference rows
 *
 * Usage (from the repository root):
 *
 *   node scripts/demo-data/seed.js
 *   node scripts/demo-data/seed.js --patients 240 --admissions 150 --quiet
 *   node scripts/demo-data/seed.js --sql-out C:\temp\demo-sql
 *
 * Connection settings come from the MySQL `--login-path` (default
 * `smart-hospital-local`), matching the other maintenance scripts.
 */

const { createDb, parseArgs } = require('./lib/db');
const { NOW, WINDOW_DAYS, refId, txId } = require('./lib/util');
const refdata = require('./lib/refdata');
const patients = require('./lib/patients');
const clinical = require('./lib/clinical');
const admissions = require('./lib/admissions');
const labs = require('./lib/labs');
const inventory = require('./lib/inventory');
const pharmacy = require('./lib/pharmacy');
const billing = require('./lib/billing');
const accounting = require('./lib/accounting');
const insights = require('./lib/insights');

function elapsed(started) {
  return ((Date.now() - started) / 1000).toFixed(1) + 's';
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const db = createDb(options);
  const patientCount = Number(options.patients || 220);
  const admissionCount = Number(options.admissions || 140);
  const activeAdmissions = Number(options['active-admissions'] || 16);

  const started = Date.now();
  db.log('Smart Hospital demo dataset — ' + NOW.toISOString());
  db.log('  window: ' + WINDOW_DAYS + ' days, patients: ' + patientCount +
    ', admissions: ' + admissionCount + ' (' + activeAdmissions + ' active)');

  // Guard against accidental double seeding ---------------------------------
  const existing = Number(db.scalar(
    "SELECT (SELECT COUNT(*) FROM hospital.patients WHERE id LIKE 'deadbeef%') + " +
    "(SELECT COUNT(*) FROM hospital.staff_members WHERE id LIKE 'feedface%')"));
  if (existing > 0) {
    db.log('');
    db.log('ABORT: the demo dataset is already present (' + existing + ' marker rows found).');
    db.log('Restore the pre-seed database backup before seeding again. See README_AR.md.');
    process.exit(2);
  }

  const ctx = { ref: refdata.load(db), windowDays: WINDOW_DAYS };

  db.log('');
  db.log('1/12 reference data');
  let step = Date.now();
  refdata.seed(db, ctx);
  db.log('      ' + elapsed(step));

  db.log('2/12 patients');
  step = Date.now();
  patients.seed(db, ctx, patientCount);
  db.log('      ' + elapsed(step));

  db.log('3/12 clinical activity (in memory)');
  step = Date.now();
  clinical.generate(ctx);
  db.log('      appointments: ' + ctx.appointments.length + ', encounters: ' + ctx.encounters.length +
    ' — ' + elapsed(step));

  db.log('4/12 inpatient stays (in memory)');
  step = Date.now();
  admissions.generate(ctx, admissionCount, activeAdmissions);
  db.log('      encounters now: ' + ctx.encounters.length + ' — ' + elapsed(step));

  db.log('5/12 inventory + procurement (in memory)');
  step = Date.now();
  inventory.generate(ctx);
  db.log('      batches: ' + ctx.inventory.batches.length + ', orders: ' + ctx.inventory.purchaseOrders.length +
    ' — ' + elapsed(step));

  db.log('6/12 pharmacy dispensing (in memory)');
  step = Date.now();
  pharmacy.generate(ctx);
  db.log('      dispenses: ' + ctx.dispenses.length + ' — ' + elapsed(step));

  db.log('7/12 billing (in memory)');
  step = Date.now();
  billing.generate(ctx);
  db.log('      invoices: ' + ctx.invoices.length + ', payments: ' + ctx.invoicePayments.length +
    ' — ' + elapsed(step));

  db.log('8/12 accounting (in memory)');
  step = Date.now();
  accounting.generate(ctx);
  db.log('      entries: ' + ctx.journalEntries.length + ' — ' + elapsed(step));

  db.log('');
  db.log('9/12 writing clinical rows');
  step = Date.now();
  clinical.insert(db, ctx);
  admissions.insert(db, ctx);
  db.log('      ' + elapsed(step));

  db.log('10/12 writing laboratory rows');
  step = Date.now();
  labs.insert(db, ctx);
  db.log('      ' + elapsed(step));

  db.log('11/12 writing inventory, pharmacy and billing rows');
  step = Date.now();
  inventory.insertProcurement(db, ctx);
  pharmacy.insert(db, ctx);
  inventory.insertLedger(db, ctx);
  billing.insert(db, ctx);
  db.log('      ' + elapsed(step));

  db.log('12/12 writing accounting and analytics rows');
  step = Date.now();
  accounting.insert(db, ctx);
  insights.generate(ctx);
  insights.insert(db, ctx);
  db.log('      ' + elapsed(step));

  db.log('');
  db.log('Done in ' + elapsed(started) + '. Next: node scripts/demo-data/verify.js');
  db.log('Marker ids: ' + txId.name + '/deadbeef for transactions, feedface for reference data.');
}

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error('');
    console.error('SEED FAILED: ' + error.message);
    if (error.stack && !String(error.message).includes('MySQL execution failed')) {
      console.error(error.stack.split('\n').slice(1, 4).join('\n'));
    }
    process.exit(1);
  }
}

module.exports = { main };
