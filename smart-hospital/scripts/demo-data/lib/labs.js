'use strict';

/*
 * Laboratory workflow. Orders come from the clinical module; this module
 * decides how far each order progressed and, for released results, produces
 * numeric/text values that respect the catalog reference range.
 */

const {
  USER, STAFF, NOW, txId, chance, pick, int, dec, weighted,
  addMinutes, sqlDateTime
} = require('./util');

function parseRange(range) {
  if (!range) return null;
  const match = /^\s*(-?\d+(?:\.\d+)?)\s*-\s*(-?\d+(?:\.\d+)?)\s*$/.exec(String(range));
  if (!match) return null;
  return { low: Number(match[1]), high: Number(match[2]) };
}

function minDate(a, b) { return a.getTime() <= b.getTime() ? a : b; }

const TEXT_RESULTS = {
  'Urinalysis': ['طبيعي', 'لا توجد خلايا صديدية', 'أثر بسيط للبروتين', 'خلايا ظهارية قليلة'],
  'Urine culture': ['لا يوجد نمو', 'نمو بكتيري مختلط', 'E. coli > 10^5 CFU/mL'],
  'ECG': ['نظم جيبي طبيعي', 'تسارع جيبي', 'تباطؤ جيبي بسيط', 'تغيرات غير نوعية في ST-T'],
  'No growth': ['لا يوجد نمو'],
  'Normal': ['طبيعي']
};

function numericResult(test, range) {
  const text = TEXT_RESULTS[test.name.split(' - ')[0]];
  if (!range) {
    return { value: null, text: pick(text || ['طبيعي']) };
  }
  const span = Math.max(0.0001, range.high - range.low);
  const bucket = weighted([['normal', 78], ['high', 12], ['low', 8], ['critical', 2]]);
  let value;
  if (bucket === 'normal') value = range.low + span * (0.12 + 0.76 * Math.random());
  else if (bucket === 'high') value = range.high + span * (0.08 + 0.55 * Math.random());
  else if (bucket === 'low') value = Math.max(0, range.low - span * (0.12 + 0.6 * Math.random()));
  else value = range.high + span * (0.9 + 0.9 * Math.random());
  return { value: Number(value.toFixed(3)), text: null };
}

function abnormalFlag(test, range, value) {
  if (value === null) return 'NORMAL';
  if (!range) return 'NORMAL';
  const span = Math.max(0.0001, range.high - range.low);
  if (value > range.high + span * 0.75 || value < range.low - span * 0.75) return 'CRITICAL';
  if (value > range.high) return 'HIGH';
  if (value < range.low) return 'LOW';
  return 'NORMAL';
}

function generate(ctx) {
  const orders = ctx.clinical.labOrders;
  const rowsOrders = [];
  const rowsItems = [];
  const rowsResults = [];
  let releasedToday = 0;
  ctx.labByEncounter = new Set();

  for (const order of orders) {
    const ageMinutes = Math.max(0, (NOW.getTime() - order.ordered_at.getTime()) / 60000);
    const old = ageMinutes > 24 * 60;
    let outcome;
    if (old) outcome = weighted([['COMPLETED', 88], ['CANCELLED', 5], ['PARTIAL', 4], ['IN_PROGRESS', 3]]);
    else if (ageMinutes > 6 * 60) outcome = weighted([['COMPLETED', 55], ['PARTIAL', 15], ['IN_PROGRESS', 20], ['ORDERED', 10]]);
    else outcome = weighted([['ORDERED', 28], ['IN_PROGRESS', 30], ['COMPLETED', 32], ['CANCELLED', 10]]);

    ctx.labByEncounter.add(order.encounter_id);
    let delivered = 0;
    for (const item of order.items) {
      const test = item.test;
      const range = parseRange(test.range);
      let status;
      if (outcome === 'CANCELLED') status = 'CANCELLED';
      else if (outcome === 'COMPLETED') status = 'REVIEWED';
      else if (outcome === 'PARTIAL') status = chance(0.55) ? 'REVIEWED' : (chance(0.5) ? 'RESULTED' : 'PROCESSING');
      else if (outcome === 'IN_PROGRESS') status = chance(0.6) ? 'COLLECTED' : 'PROCESSING';
      else status = 'PENDING';

      let collectedAt = null;
      let collectedBy = null;
      if (status !== 'PENDING' && status !== 'CANCELLED') {
        collectedAt = addMinutes(order.ordered_at, int(10, 120));
        if (collectedAt.getTime() > NOW.getTime()) collectedAt = addMinutes(NOW, -int(30, 90));
        collectedBy = pick([STAFF.LABTECH1, ctx.newStaff.labtech2].filter(Boolean));
      }

      if (status === 'REVIEWED' || status === 'RESULTED') {
        const result = numericResult(test, range);
        // Keep the analytical chain in the past and strictly ordered:
        // collection → performed → reviewed → released.
        const performedAt = minDate(
          addMinutes(collectedAt, int(15, Math.max(20, test.minutes))),
          addMinutes(NOW, -25));
        const reviewedAt = minDate(addMinutes(performedAt, int(10, 50)), addMinutes(NOW, -12));
        const proposedRelease = addMinutes(reviewedAt, int(0, 25));
        const releasedAt = proposedRelease.getTime() <= NOW.getTime() ? proposedRelease : null;
        // A result that has a release timestamp must carry its reviewer; one
        // whose release would fall in the future stays RESULTED (not released).
        const reviewed = releasedAt !== null;
        status = reviewed ? 'REVIEWED' : 'RESULTED';
        rowsResults.push({
          id: txId(), lab_order_item_id: item.id,
          result_text: result.text, result_numeric: result.value, result_unit: test.unit,
          reference_range: test.range, abnormal_flag: abnormalFlag(test, range, result.value),
          performed_by: collectedBy, reviewed_by: reviewed ? STAFF.LABSUP1 : null,
          performed_at: performedAt, reviewed_at: reviewed ? reviewedAt : null,
          released_at: releasedAt
        });
        if (reviewed && releasedAt.toISOString().slice(0, 10) === NOW.toISOString().slice(0, 10)) releasedToday += 1;
      }

      rowsItems.push({
        id: item.id, lab_order_id: order.id, lab_test_id: test.id, status,
        specimen_code: collectedAt ? 'SPEC-' + int(100000, 999999) : null,
        collected_at: collectedAt, collected_by: collectedBy
      });
      if (status === 'REVIEWED') delivered += 1;
    }

    let status;
    if (outcome === 'CANCELLED') status = 'CANCELLED';
    else if (rowsItems.filter((row) => row.lab_order_id === order.id).every((row) => row.status === 'REVIEWED')) status = 'COMPLETED';
    else if (delivered > 0) status = 'PARTIALLY_COMPLETED';
    else if (rowsItems.filter((row) => row.lab_order_id === order.id).some((row) => ['COLLECTED', 'PROCESSING'].includes(row.status))) status = 'IN_PROGRESS';
    else status = 'ORDERED';

    rowsOrders.push({
      id: order.id, encounter_id: order.encounter_id, ordered_by: order.ordered_by,
      priority: order.priority, status, clinical_question: order.clinical_question,
      ordered_at: order.ordered_at, branch_id: order.branch_id
    });
  }

  ctx.labOrders = rowsOrders;
  ctx.labOrderItems = rowsItems;
  ctx.labResults = rowsResults;

  dbInsert(ctx.db, rowsOrders, rowsItems, rowsResults);
  ctx.db.log('  lab orders: ' + rowsOrders.length + ', items: ' + rowsItems.length +
    ', results: ' + rowsResults.length + ' (released today: ' + releasedToday + ')');
}

function dbInsert(db, orders, items, results) {
  db.insert('lab_orders',
    ['id', 'encounter_id', 'ordered_by', 'priority', 'status', 'clinical_question', 'ordered_at', 'branch_id'],
    orders, 300);
  db.insert('lab_order_items',
    ['id', 'lab_order_id', 'lab_test_id', 'status', 'specimen_code', 'collected_at', 'collected_by'],
    items, 400);
  db.insert('lab_results',
    ['id', 'lab_order_item_id', 'result_text', 'result_numeric', 'result_unit', 'reference_range',
      'abnormal_flag', 'performed_by', 'reviewed_by', 'performed_at', 'reviewed_at', 'released_at'],
    results, 400);
}

module.exports = { generate, insert: (db, ctx) => { ctx.db = db; generate(ctx); } };
