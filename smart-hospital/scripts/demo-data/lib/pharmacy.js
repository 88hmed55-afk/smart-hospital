'use strict';

/*
 * Pharmacy dispensing. Each active prescription is fulfilled as close to its
 * ordering time as possible, allocating stock first-expiry-first-out across the
 * batches produced by the inventory module. Allocations are limited to what is
 * actually on hand at that instant, so the ledger never goes negative and
 * anything that cannot be covered stays ACTIVE / PARTIALLY_DISPENSED for the
 * pharmacy queue.
 */

const {
  BRANCH_ID, USER, STAFF, PHARMACY_LOCATION, NOW,
  txId, chance, pick, int, weighted, addMinutes, localDate, dayStartLocal
} = require('./util');
const inventory = require('./inventory');

function hoursBetween(a, b) { return (b.getTime() - a.getTime()) / 3600000; }

function generate(ctx) {
  const store = ctx.inventory;
  const dispenses = [];
  const dispenseItems = [];
  const stats = { full: 0, partial: 0, skipped: 0, queued: 0, items: 0 };
  const pharmacist = pick([STAFF.PHARMACIST1, ctx.newStaff.pharmacist2].filter(Boolean));

  const prescriptions = ctx.clinical.prescriptions
    .filter((prescription) => prescription.status === 'ACTIVE')
    .sort((a, b) => a.ordered_at.getTime() - b.ordered_at.getTime());

  store.sim = new Map();
  store.cursor = 0;

  for (const prescription of prescriptions) {
    // Prescriptions written in the last half hour are still in the queue.
    const ageHours = hoursBetween(prescription.ordered_at, NOW);
    if (ageHours < 0.5) { stats.queued += 1; continue; }
    // A small share of patients never collect their medication.
    if (chance(0.07)) { stats.skipped += 1; continue; }

    let dispensedAt = addMinutes(prescription.ordered_at, int(25, 600));
    const latest = addMinutes(NOW, -20);
    if (dispensedAt.getTime() > latest.getTime()) dispensedAt = latest;
    if (dispensedAt.getTime() <= prescription.ordered_at.getTime()) { stats.queued += 1; continue; }

    inventory.advanceTo(store, dispensedAt);
    const cutoff = dayStartLocal(dispensedAt).getTime();

    const dispenseId = txId();
    const allocations = [];
    let fullyCovered = 0;
    let partlyCovered = 0;

    for (const item of prescription.items) {
      const batches = (store.batchesByMed.get(item.medication_id) || [])
        .filter((batch) => batch.expiresAt.getTime() >= cutoff)
        .sort((a, b) => (a.expiresAt.getTime() - b.expiresAt.getTime()) || (a.id < b.id ? -1 : 1));
      let remaining = inventory.round3(item.quantity_prescribed);
      let allocatedForItem = 0;
      for (const batch of batches) {
        if (remaining <= 0) break;
        const available = inventory.available(store, batch.id);
        if (available <= 0) continue;
        const quantity = inventory.round3(Math.min(remaining, available));
        if (quantity <= 0) continue;
        inventory.take(store, batch.id, quantity);
        allocations.push({
          batch, prescriptionItem: item, quantity, dispensedAt
        });
        allocatedForItem = inventory.round3(allocatedForItem + quantity);
        remaining = inventory.round3(remaining - quantity);
      }
      if (allocatedForItem >= item.quantity_prescribed - 0.0005) fullyCovered += 1;
      else if (allocatedForItem > 0) partlyCovered += 1;
    }

    if (!allocations.length) { stats.skipped += 1; continue; }

    dispenses.push({
      id: dispenseId, prescription_id: prescription.id, dispensed_by: pharmacist,
      inventory_location_id: PHARMACY_LOCATION, status: 'POSTED',
      dispensed_at: dispensedAt, branch_id: BRANCH_ID,
      idempotency_key: null, request_fingerprint: null
    });

    for (const allocation of allocations) {
      dispenseItems.push({
        id: txId(), dispense_id: dispenseId,
        prescription_item_id: allocation.prescriptionItem.id,
        batch_id: allocation.batch.id,
        quantity_dispensed: allocation.quantity
      });
    }

    // One movement per (dispense, batch): the database enforces that pairing.
    const perBatch = new Map();
    for (const allocation of allocations) {
      perBatch.set(allocation.batch.id, inventory.round3((perBatch.get(allocation.batch.id) || 0) + allocation.quantity));
    }
    for (const [batchId, quantity] of perBatch) {
      store.dispenseMovements.push({
        id: txId(), location_id: PHARMACY_LOCATION, batch_id: batchId,
        movement_type: 'DISPENSE', quantity_delta: -quantity,
        reference_type: 'DISPENSE', reference_id: dispenseId,
        transfer_group_id: null, reason: 'صرف وصفة طبية',
        performed_by: USER.PHARMACIST, occurred_at: dispensedAt
      });
    }

    const itemCount = prescription.items.length;
    if (fullyCovered === itemCount) {
      prescription.status = 'DISPENSED';
      stats.full += 1;
    } else {
      prescription.status = 'PARTIALLY_DISPENSED';
      stats.partial += 1;
      if (partlyCovered === 0 && fullyCovered === 0) {
        // Nothing was covered but allocations exist — keep the dispense for audit.
        stats.partial += 0;
      }
    }
    stats.items += allocations.length;
  }

  ctx.dispenses = dispenses;
  ctx.dispenseItems = dispenseItems;
  ctx.pharmacyStats = stats;
  return { dispenses, dispenseItems, stats };
}

function insert(db, ctx) {
  db.insert('dispenses',
    ['id', 'prescription_id', 'dispensed_by', 'inventory_location_id', 'status', 'dispensed_at',
      'branch_id', 'idempotency_key', 'request_fingerprint'],
    ctx.dispenses, 300);
  db.insert('dispense_items',
    ['id', 'dispense_id', 'prescription_item_id', 'batch_id', 'quantity_dispensed'],
    ctx.dispenseItems, 400);
  const stats = ctx.pharmacyStats;
  db.log('  dispenses: ' + ctx.dispenses.length + ' (' + stats.full + ' full, ' +
    stats.partial + ' partial), items: ' + ctx.dispenseItems.length +
    ', not dispensed: ' + stats.skipped + ', queued: ' + stats.queued);
}

module.exports = { generate, insert };
