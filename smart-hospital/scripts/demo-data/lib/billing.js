'use strict';

/*
 * Billing: one invoice per completed encounter (a small share intentionally
 * left unbilled so the cashier queue has work), service lines priced from the
 * catalog, and payments that agree with the invoice status. Every invoice is
 * internally consistent with the database checks:
 *
 *   total_amount = subtotal - discount_amount + tax_amount
 *   line_total   = quantity * unit_price - line_discount
 */

const {
  BRANCH_ID, USER, CURRENCY, NOW,
  txId, chance, pick, int, dec, weighted, addMinutes, addDays,
  localDate, dayStartLocal
} = require('./util');

function round2(value) { return Math.round(value * 100) / 100; }

function serviceMap(ctx) {
  const map = {};
  for (const service of ctx.ref.services) map[service.code] = service;
  return map;
}

function pushService(lines, service, quantity) {
  if (!service) return;
  const existing = lines.find((line) => line.service.id === service.id);
  if (existing) {
    existing.quantity = Math.round((existing.quantity + quantity) * 1000) / 1000;
    return;
  }
  lines.push({ service, quantity });
}

function admissionStayDays(ctx, admission) {
  const end = admission.discharged_at ? admission.discharged_at : NOW;
  const days = Math.ceil((end.getTime() - admission.admitted_at.getTime()) / 86400000);
  return Math.min(12, Math.max(1, days));
}

function generate(ctx) {
  const services = serviceMap(ctx);
  const invoices = [];
  const invoiceItems = [];
  const payments = [];
  const invoiceSeq = {};
  const receiptSeq = {};
  const stats = { paid: 0, partial: 0, issued: 0, void: 0, draft: 0, unbilled: 0 };

  const admissionByEncounter = {};
  for (const admission of (ctx.admissions || [])) admissionByEncounter[admission.encounter_id] = admission;

  let paymentCounter = 0;
  const sorted = ctx.encounters
    .filter((encounter) => encounter.status === 'COMPLETED'
      || (encounter.encounter_type === 'INPATIENT' && encounter.status === 'IN_PROGRESS'))
    .sort((a, b) => (a.ended_at || a.started_at).getTime() - (b.ended_at || b.started_at).getTime());

  for (const encounter of sorted) {
    if (chance(0.12)) { stats.unbilled += 1; continue; }
    const closedAt = encounter.ended_at || addMinutes(NOW, -int(60, 180));
    if (closedAt.getTime() > NOW.getTime() - 45 * 60000) { stats.unbilled += 1; continue; }

    const lines = [];
    const isInpatient = encounter.encounter_type === 'INPATIENT';
    const admission = admissionByEncounter[encounter.id];

    if (isInpatient && admission) {
      const days = admissionStayDays(ctx, admission);
      const roomCode = weighted([['ROOM-PRIV', 45], ['ROOM-GEN', 40], ['ROOM-ICU', 15]]);
      pushService(lines, services[roomCode], days);
      pushService(lines, services[isInpatient ? (chance(0.6) ? 'CONS-SPEC' : 'CONS-GEN') : 'CONS-GEN'], 1);
    } else {
      pushService(lines, services[chance(0.34) ? 'CONS-SPEC' : 'CONS-GEN'], 1);
      if (chance(0.16)) pushService(lines, services['CONS-FUP'], 1);
    }

    if (chance(0.42)) {
      pushService(lines, services[chance(0.5) ? 'LAB-PANEL' : 'LAB-CBC'], 1);
    }
    if (chance(0.24)) {
      pushService(lines, services[weighted([['XR-CHEST', 40], ['US-ABD', 30], ['XR-KNEE', 30]])], 1);
    }
    if (chance(0.3)) {
      const procedure = weighted([
        ['PROC-DRESS', 26], ['ECG-12', 20], ['NEBULIZER', 18], ['PROC-SUTURE', 12],
        ['PHYSIO', 14], ['VACC-FLU', 10]
      ]);
      pushService(lines, services[procedure], 1);
    }
    if (isInpatient && chance(0.45)) {
      pushService(lines, services['LAB-CBC'], 1);
    }
    if (!lines.length) { stats.unbilled += 1; continue; }

    const createdAt = addMinutes(closedAt, int(5, 120));
    if (createdAt.getTime() > NOW.getTime() - 15 * 60000) { stats.unbilled += 1; continue; }
    const issuedAt = addMinutes(createdAt, int(3, 90));
    if (issuedAt.getTime() > NOW.getTime() - 5 * 60000) { stats.unbilled += 1; continue; }

    const hoursOld = (NOW.getTime() - issuedAt.getTime()) / 3600000;
    let status;
    if (hoursOld < 8) status = weighted([['DRAFT', 14], ['ISSUED', 46], ['PAID', 32], ['PARTIALLY_PAID', 8]]);
    else if (hoursOld < 96) status = weighted([['DRAFT', 5], ['ISSUED', 28], ['PAID', 55], ['PARTIALLY_PAID', 12]]);
    else status = weighted([['PAID', 68], ['PARTIALLY_PAID', 12], ['ISSUED', 16], ['VOID', 4]]);

    let subtotal = 0;
    const itemRows = [];
    for (const line of lines) {
      const unitPrice = line.service.price;
      const quantity = Math.round(line.quantity * 1000) / 1000;
      const lineTotal = round2(quantity * unitPrice);
      subtotal += lineTotal;
      itemRows.push({
        id: txId(),
        service_id: line.service.id,
        description_snapshot: line.service.name,
        quantity,
        unit_price: unitPrice,
        line_discount: 0,
        line_total: lineTotal
      });
    }
    subtotal = round2(subtotal);
    const discount = chance(0.12) ? round2(subtotal * dec(0.05, 0.2, 2)) : 0;
    const total = round2(subtotal - discount);

    const id = txId();
    const dayKey = localDate(createdAt).replace(/-/g, '');
    invoiceSeq[dayKey] = (invoiceSeq[dayKey] || 0) + 1;

    invoices.push({
      id, branch_id: BRANCH_ID,
      invoice_number: 'INV-' + dayKey + '-' + String(invoiceSeq[dayKey]).padStart(4, '0'),
      patient_id: encounter.patient_id,
      encounter_id: encounter.id,
      status,
      currency_code: CURRENCY,
      subtotal, discount_amount: discount, tax_amount: 0, total_amount: total,
      issued_at: status === 'DRAFT' ? null : issuedAt,
      created_by: chance(0.7) ? USER.RECEPTION : USER.ACCOUNTANT,
      created_at: createdAt, version: 0,
      items: itemRows
    });
    for (const item of itemRows) {
      invoiceItems.push(Object.assign({}, item, { invoice_id: id }));
    }

    if (status === 'PAID' || status === 'PARTIALLY_PAID') {
      const paidAt = new Date(Math.min(
        addMinutes(issuedAt, int(5, 60 * 24 * 10)).getTime(),
        NOW.getTime() - 2 * 60000));
      // Never bill a receipt in the future, and keep it after the invoice.
      const payTime = new Date(Math.min(
        Math.max(paidAt.getTime(), addMinutes(issuedAt, 2).getTime()),
        NOW.getTime() - 60000));
      const method = weighted([['CASH', 48], ['CARD', 24], ['BANK_TRANSFER', 16], ['INSURANCE', 10], ['OTHER', 2]]);
      const slices = status === 'PAID'
        ? (chance(0.28) ? [0.6, 0.4] : [1])
        : [dec(0.2, 0.72, 2)];
      let remaining = status === 'PAID' ? total : round2(total * slices[0]);
      if (remaining > total) remaining = total;
      const receiptKey = localDate(payTime).replace(/-/g, '');
      let sliceIndex = 0;
      while (remaining > 0 && sliceIndex < slices.length) {
        paymentCounter += 1;
        receiptSeq[receiptKey] = (receiptSeq[receiptKey] || 0) + 1;
        const amount = sliceIndex === slices.length - 1
          ? round2(remaining)
          : round2(remaining * slices[sliceIndex]);
        if (amount <= 0) { sliceIndex += 1; continue; }
        const payMethod = sliceIndex === 0 ? method : 'CASH';
        payments.push({
          id: txId(), invoice_id: id,
          receipt_number: 'RCPT-' + receiptKey + '-' + String(receiptSeq[receiptKey]).padStart(4, '0'),
          amount, currency_code: CURRENCY, payment_method: payMethod, status: 'POSTED',
          transaction_reference: payMethod === 'CASH' ? null
            : 'TRX-' + String(100000 + paymentCounter),
          received_by: chance(0.7) ? USER.RECEPTION : USER.ACCOUNTANT,
          paid_at: payTime, idempotency_key: null, request_fingerprint: null, version: 0
        });
        remaining = round2(remaining - amount);
        sliceIndex += 1;
        if (sliceIndex >= slices.length && remaining > 0.009) {
          slices.push(0);
        }
      }
    }

    if (status === 'PAID') stats.paid += 1;
    else if (status === 'PARTIALLY_PAID') stats.partial += 1;
    else if (status === 'ISSUED') stats.issued += 1;
    else if (status === 'VOID') stats.void += 1;
    else stats.draft += 1;
  }

  ctx.invoices = invoices;
  ctx.invoiceItems = invoiceItems;
  ctx.invoicePayments = payments;
  ctx.billingStats = stats;
  return { invoices, invoiceItems, payments, stats };
}

function insert(db, ctx) {
  db.insert('invoices',
    ['id', 'branch_id', 'invoice_number', 'patient_id', 'encounter_id', 'status', 'currency_code',
      'subtotal', 'discount_amount', 'tax_amount', 'total_amount', 'issued_at', 'created_by',
      'created_at', 'version'],
    ctx.invoices, 300);
  db.insert('invoice_items',
    ['id', 'invoice_id', 'service_id', 'dispense_item_id', 'description_snapshot', 'quantity',
      'unit_price', 'line_discount', 'line_total'],
    ctx.invoiceItems.map((item) => Object.assign({ dispense_item_id: null }, item)), 400);
  db.insert('payments',
    ['id', 'invoice_id', 'receipt_number', 'amount', 'currency_code', 'payment_method', 'status',
      'transaction_reference', 'received_by', 'paid_at', 'idempotency_key', 'request_fingerprint',
      'version'], ctx.invoicePayments, 400);
  const stats = ctx.billingStats;
  db.log('  invoices: ' + ctx.invoices.length + ' (' + stats.paid + ' paid, ' + stats.partial +
    ' partial, ' + stats.issued + ' issued, ' + stats.void + ' void, ' + stats.draft + ' draft), items: ' +
    ctx.invoiceItems.length + ', payments: ' + ctx.invoicePayments.length);
}

module.exports = { generate, insert };
