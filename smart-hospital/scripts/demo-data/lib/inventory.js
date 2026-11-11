'use strict';

/*
 * Pharmacy inventory, split in two halves:
 *
 *   1. `generate` builds a deterministic six-month procurement and stock
 *      history: opening balances, purchase orders through every workflow
 *      state, goods receipts with new batches, and a handful of damage /
 *      adjustment write-offs.
 *   2. `advanceTo` replays that history up to a point in time so the pharmacy
 *      module can allocate dispenses FEFO without ever going negative.
 *
 * Every quantity is kept on the `stock_movements` ledger; `stock_balances` is
 * derived from the same numbers, so the two can never disagree.
 */

const {
  BRANCH_ID, USER, PHARMACY_LOCATION, STORE_LOCATION, NOW, WINDOW_START,
  txId, refId, chance, pick, int, dec, weighted, shuffle,
  addMinutes, addDays, dayStartLocal, workingInstant, localDate, sqlDate
} = require('./util');

function round2(value) { return Math.round(value * 100) / 100; }
function round3(value) { return Math.round(value * 1000) / 1000; }

const MANUFACTURERS = [
  'شركة الأدوية الوطنية', 'مختبرات اليمن الدوائية', 'الشركة العربية للتصنيع الدوائي',
  'مصنع الشفاء للمستحضرات', 'المختبرات الحديثة للأدوية'
];

function orderQuantity(cost) {
  if (cost <= 80) return int(600, 1500);
  if (cost <= 200) return int(300, 800);
  if (cost <= 1000) return int(100, 300);
  return int(30, 100);
}

function movementCmp(a, b) {
  const delta = a.occurred_at.getTime() - b.occurred_at.getTime();
  if (delta !== 0) return delta;
  const rank = (movement) => {
    if (movement.quantity_delta > 0) return 0;
    if (movement.movement_type === 'ADJUSTMENT') return 1;
    return 2;
  };
  return rank(a) - rank(b);
}

function generate(ctx) {
  const costs = ctx.medCosts || {};
  const demoMeds = ctx.ref.medications.filter((medication) => medication.code.indexOf('DEMO2-MED-') === 0);
  const store = {
    batches: [], purchaseOrders: [], poItems: [], approvals: [],
    goodsReceipts: [], receiptItems: [], fixed: [], dispenseMovements: [],
    batchesByMed: new Map(), sim: new Map(), cursor: 0, finalBalances: new Map()
  };

  const openAt = addDays(dayStartLocal(WINDOW_START), -2);
  let batchSeq = 0;
  let poSeq = 0;
  let receiptSeq = 0;
  const poDaySeq = {};
  const receiptDaySeq = {};

  function addBatch(medication, createdAt, manufacturedOn, expiresOn, prefix) {
    batchSeq += 1;
    const code = medication.code.replace('DEMO2-MED-', '');
    const batch = {
      id: refId(),
      medication_id: medication.id,
      batch_number: prefix + '-' + code + '-' + localDate(createdAt).replace(/-/g, '') + '-' + String(batchSeq).padStart(3, '0'),
      manufacturer: pick(MANUFACTURERS),
      manufactured_on: sqlDate(manufacturedOn),
      expires_on: sqlDate(expiresOn),
      created_at: createdAt,
      expiresAt: expiresOn
    };
    store.batches.push(batch);
    if (!store.batchesByMed.has(medication.id)) store.batchesByMed.set(medication.id, []);
    store.batchesByMed.get(medication.id).push(batch);
    return batch;
  }

  function receipt(batch, quantity, at, referenceType, referenceId, reason) {
    store.fixed.push({
      id: txId(), location_id: PHARMACY_LOCATION, batch_id: batch.id,
      movement_type: 'RECEIPT', quantity_delta: round3(quantity),
      reference_type: referenceType, reference_id: referenceId,
      transfer_group_id: null, reason, performed_by: USER.INVENTORY, occurred_at: at
    });
  }

  // Opening balances -------------------------------------------------------
  for (const medication of demoMeds) {
    const cost = costs[medication.id] || 80;

    // Main shelf stock with a comfortable shelf life.
    const main = addBatch(medication, openAt,
      addDays(openAt, -int(240, 500)), addDays(NOW, int(320, 640)), 'OPEN');
    receipt(main, int(900, 1500), openAt, 'OPENING_BALANCE', null, 'رصيد افتتاحي - جرد بداية الفترة');

    // A second lot that expires sooner; FEFO consumes it first.
    const near = addBatch(medication, openAt,
      addDays(openAt, -int(540, 700)), addDays(NOW, int(35, 110)), 'OPEN');
    receipt(near, int(220, 520), openAt, 'OPENING_BALANCE', null, 'رصيد افتتاحي - دفعة قريبة الانتهاء');

    // Expired lots produce genuine expiry write-offs in the period.
    if (cost <= 400 && chance(0.42)) {
      const expiredAt = addDays(NOW, -int(20, 95));
      const expired = addBatch(medication, addDays(openAt, -int(120, 260)),
        addDays(expiredAt, -int(700, 900)), expiredAt, 'EXP');
      const qty = int(60, 200);
      receipt(expired, qty, addDays(expiredAt, -int(190, 320)), 'OPENING_BALANCE', null, 'رصيد افتتاحي - دفعة قديمة');
      store.fixed.push({
        id: txId(), location_id: PHARMACY_LOCATION, batch_id: expired.id,
        movement_type: 'EXPIRED', quantity_delta: -qty,
        reference_type: 'EXPIRY_WRITE_OFF', reference_id: null, transfer_group_id: null,
        reason: 'شطب تلقائي لدفعة منتهية الصلاحية', performed_by: USER.INVENTORY,
        occurred_at: addDays(expiredAt, int(2, 6))
      });
    }
  }

  // Purchase orders --------------------------------------------------------
  const cycles = [
    { offset: 9, count: 3, mode: 'mature' },
    { offset: 24, count: 3, mode: 'mature' },
    { offset: 41, count: 3, mode: 'mature' },
    { offset: 58, count: 3, mode: 'mature' },
    { offset: 76, count: 3, mode: 'mature' },
    { offset: 94, count: 3, mode: 'mature' },
    { offset: 111, count: 3, mode: 'mature' },
    { offset: 128, count: 3, mode: 'mature' },
    { offset: 144, count: 2, mode: 'recent' },
    { offset: 158, count: 2, mode: 'recent' },
    { offset: 169, count: 2, mode: 'open' },
    { offset: 176, count: 2, mode: 'open' }
  ];

  for (const cycle of cycles) {
    const day = dayStartLocal(addDays(WINDOW_START, cycle.offset));
    for (let i = 0; i < cycle.count; i += 1) {
      const createdAt = addMinutes(workingInstant(day, 9, 14), int(0, 55));
      if (createdAt.getTime() > NOW.getTime() - 3600000) continue;

      let status;
      if (cycle.mode === 'mature') {
        status = weighted([['RECEIVED', 76], ['PARTIALLY_RECEIVED', 12], ['CANCELLED', 4], ['REJECTED', 4], ['ORDERED', 4]]);
      } else if (cycle.mode === 'recent') {
        status = weighted([['RECEIVED', 44], ['PARTIALLY_RECEIVED', 16], ['ORDERED', 18], ['APPROVED', 12], ['SUBMITTED', 10]]);
      } else {
        status = weighted([['DRAFT', 22], ['SUBMITTED', 20], ['APPROVED', 20], ['ORDERED', 26], ['RECEIVED', 6], ['PARTIALLY_RECEIVED', 6]]);
      }

      const wantsReceipt = status === 'RECEIVED' || status === 'PARTIALLY_RECEIVED';
      const receiptAt = addDays(createdAt, int(4, 12));
      if (wantsReceipt && receiptAt.getTime() > NOW.getTime() - 12 * 3600000) {
        status = 'ORDERED';
      }

      const medicationCount = int(2, 5);
      const meds = shuffle(demoMeds).slice(0, medicationCount);
      const id = txId();
      poSeq += 1;
      const key = localDate(createdAt).replace(/-/g, '');
      poDaySeq[key] = (poDaySeq[key] || 0) + 1;
      const supplier = pick(ctx.ref.suppliers);
      const items = [];
      let subtotal = 0;
      meds.forEach((medication, index) => {
        const cost = costs[medication.id] || 60;
        const quantity = orderQuantity(cost);
        const unitCost = round2(cost * dec(0.9, 1.25, 2));
        const lineTotal = round2(quantity * unitCost);
        subtotal += lineTotal;
        items.push({
          id: txId(), purchase_order_id: id, line_number: index + 1,
          medication_id: medication.id,
          item_description_snapshot: medication.generic + ' ' + medication.strength + ' — ' + medication.form,
          ordered_quantity: quantity, unit_of_measure: medication.unit || 'unit',
          unit_cost: unitCost, line_discount: 0, line_total: lineTotal,
          medication,
          received: 0
        });
      });
      subtotal = round2(subtotal);
      const discount = chance(0.16) ? round2(subtotal * dec(0.01, 0.04, 2)) : 0;
      const tax = 0;
      const total = round2(subtotal - discount + tax);
      const approved = ['APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'REJECTED'].includes(status)
        || (status === 'CANCELLED' && chance(0.5));
      const decidedAt = addMinutes(createdAt, int(30, 300));
      const orderedAt = ['ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED'].includes(status)
        ? addMinutes(decidedAt, int(30, 240))
        : null;

      store.purchaseOrders.push({
        id, branch_id: BRANCH_ID, supplier_id: supplier.id,
        order_number: 'PO-' + key + '-' + String(poDaySeq[key]).padStart(3, '0'),
        order_status: status, currency_code: 'YER',
        subtotal, discount_amount: discount, tax_amount: tax, total_amount: total,
        requested_by: USER.INVENTORY, approved_by: approved ? USER.MANAGER : null,
        ordered_at: orderedAt, expected_delivery_on: sqlDate(addDays(createdAt, int(7, 15))),
        notes: pick([
          'طلب دوري لتغطية استهلاك الصيدلية الشهري.',
          'إعادة تعبئة أصناف وصلت إلى حد إعادة الطلب.',
          'طلب عاجل لتفادي نفاد الأدوية الأساسية.',
          'توريد ربع سنوي معتمد من إدارة المخزون.',
          'طلب تكميلي بناءً على تقرير الاستهلاك الأسبوعي.'
        ]),
        created_at: createdAt, updated_at: orderedAt || decidedAt, version: 0
      });
      for (const item of items) store.poItems.push(item);

      if (status !== 'DRAFT') {
        let decision = 'PENDING';
        if (status === 'REJECTED') decision = 'REJECTED';
        else if (status !== 'SUBMITTED') decision = 'APPROVED';
        store.approvals.push({
          id: txId(), purchase_order_id: id, approval_step: 1,
          approver_user_id: decision === 'PENDING' ? null : USER.MANAGER,
          decision, decision_note: decision === 'REJECTED'
            ? 'السعر أعلى من الميزانية المعتمدة؛ يُعاد التفاوض.'
            : (decision === 'APPROVED' ? 'اعتماد الطلب حسب سياسة المشتريات.' : null),
          decided_at: decision === 'PENDING' ? null : decidedAt,
          created_at: createdAt
        });
      }

      if (wantsReceipt && status !== 'ORDERED' && receiptAt.getTime() <= NOW.getTime() - 3600000) {
        receiptSeq += 1;
        const receiptId = txId();
        const receiptKey = localDate(receiptAt).replace(/-/g, '');
        receiptDaySeq[receiptKey] = (receiptDaySeq[receiptKey] || 0) + 1;
        const postedAt = addMinutes(receiptAt, int(20, 150));
        store.goodsReceipts.push({
          id: receiptId, branch_id: BRANCH_ID, purchase_order_id: id,
          inventory_location_id: PHARMACY_LOCATION,
          receipt_number: 'GRN-' + receiptKey + '-' + String(receiptDaySeq[receiptKey]).padStart(3, '0'),
          supplier_delivery_reference: 'DN-' + int(100000, 999999),
          receipt_status: 'POSTED', received_by: USER.INVENTORY, posted_by: USER.INVENTORY,
          received_at: receiptAt, posted_at: postedAt,
          notes: status === 'PARTIALLY_RECEIVED' ? 'استلام جزئي؛ بانتظار الكمية المتبقية.' : null,
          created_at: receiptAt
        });
        for (const item of items) {
          const full = status === 'RECEIVED';
          const accepted = full ? item.ordered_quantity : Math.max(1, Math.round(item.ordered_quantity * dec(0.45, 0.8, 2)));
          let rejected = 0;
          if (chance(0.12)) {
            rejected = Math.min(item.ordered_quantity - accepted, int(2, 12));
            if (rejected < 0) rejected = 0;
          }
          if (accepted <= 0) continue;
          const manufacturedOn = addDays(receiptAt, -int(40, 320));
          const batch = addBatch(item.medication, receiptAt, manufacturedOn,
            addDays(receiptAt, int(420, 900)), 'B');
          store.receiptItems.push({
            id: txId(), receipt_id: receiptId, purchase_order_id: id,
            purchase_order_item_id: item.id, medication_id: item.medication_id,
            medication_batch_id: batch.id, accepted_quantity: accepted,
            rejected_quantity: rejected,
            rejection_reason: rejected > 0 ? pick(['عبوة تالفة أثناء النقل', 'تاريخ صلاحية قريب جداً', 'ملصق غير مطابق للمواصفات']) : null,
            created_at: receiptAt
          });
          item.received = accepted;
          receipt(batch, accepted, postedAt, 'GOODS_RECEIPT', receiptId, 'استلام أمر شراء ' + receiptId.slice(0, 8));
        }
      }
    }
  }

  // Damage and cycle-count adjustments -------------------------------------
  const candidates = store.batches.filter((batch) => batch.batch_number.indexOf('EXP-') !== 0);
  for (let i = 0; i < 9 && candidates.length; i += 1) {
    const batch = pick(candidates);
    const at = addDays(dayStartLocal(WINDOW_START), int(20, 170)) ;
    at.setUTCHours(int(9, 16), int(0, 59), 0, 0);
    if (at.getTime() > NOW.getTime()) continue;
    store.fixed.push({
      id: txId(), location_id: PHARMACY_LOCATION, batch_id: batch.id,
      movement_type: 'DAMAGE', quantity_delta: -int(3, 18),
      reference_type: 'STOCK_COUNT', reference_id: null, transfer_group_id: null,
      reason: pick(['تلف عبوات بسبب الرطوبة', 'كسر أثناء المناولة', 'عبوات غير صالحة للاستخدام']),
      performed_by: USER.INVENTORY, occurred_at: at
    });
  }
  for (let i = 0; i < 6 && candidates.length; i += 1) {
    const batch = pick(candidates);
    const at = addDays(dayStartLocal(WINDOW_START), int(25, 170));
    at.setUTCHours(int(9, 16), int(0, 59), 0, 0);
    if (at.getTime() > NOW.getTime()) continue;
    store.fixed.push({
      id: txId(), location_id: PHARMACY_LOCATION, batch_id: batch.id,
      movement_type: 'ADJUSTMENT', quantity_delta: int(4, 25),
      reference_type: 'STOCK_COUNT', reference_id: null, transfer_group_id: null,
      reason: 'تسوية جرد دوري - فرق لصالح المخزون',
      performed_by: USER.INVENTORY, occurred_at: at
    });
  }

  // Fixed movements are finalised once so the FEFO replay sees exact numbers.
  store.fixed.sort(movementCmp);
  const running = new Map();
  const kept = [];
  for (const movement of store.fixed) {
    let delta = movement.quantity_delta;
    if (delta < 0) {
      const available = round3(running.get(movement.batch_id) || 0);
      if (available <= 0) continue;
      delta = -round3(Math.min(available, -delta));
      movement.quantity_delta = delta;
    }
    running.set(movement.batch_id, round3((running.get(movement.batch_id) || 0) + delta));
    kept.push(movement);
  }
  store.fixed = kept;

  ctx.inventory = store;
  return store;
}

// Apply every fixed movement whose timestamp is at or before `time`.
function advanceTo(store, time) {
  while (store.cursor < store.fixed.length
    && store.fixed[store.cursor].occurred_at.getTime() <= time.getTime()) {
    const movement = store.fixed[store.cursor];
    store.cursor += 1;
    store.sim.set(movement.batch_id, round3((store.sim.get(movement.batch_id) || 0) + movement.quantity_delta));
  }
}

function available(store, batchId) {
  return round3(store.sim.get(batchId) || 0);
}

function take(store, batchId, quantity) {
  store.sim.set(batchId, round3((store.sim.get(batchId) || 0) - quantity));
}

// Remove or shrink any fixed negative movement that would overdraw a batch
// once the dispense timeline is merged in. Dispenses are never altered: they
// were allocated against the live balance, so an overdraw here is a bug.
function reconcile(store) {
  const merged = store.fixed.concat(store.dispenseMovements).sort(movementCmp);
  const balance = new Map();
  const fixed = [];
  const dispenses = [];
  for (const movement of merged) {
    const current = round3(balance.get(movement.batch_id) || 0);
    let delta = movement.quantity_delta;
    if (movement.movement_type === 'DISPENSE') {
      if (-delta > current + 0.0005) {
        throw new Error('Dispense overdraws batch ' + movement.batch_id + ' by ' + round3(-delta - current));
      }
      dispenses.push(movement);
    } else {
      if (delta < 0) {
        delta = -round3(Math.min(current, -delta));
        if (delta === 0) continue;
        movement.quantity_delta = delta;
      }
      fixed.push(movement);
    }
    balance.set(movement.batch_id, round3(current + delta));
  }
  store.fixed = fixed;
  store.dispenseMovements = dispenses;
}

// Drain the fixed timeline and derive stock_balances from the final ledger.
function close(store) {
  reconcile(store);
  const balances = [];
  const seen = new Set();
  const all = store.fixed.concat(store.dispenseMovements);
  const totals = new Map();
  for (const movement of all) {
    const key = movement.location_id + '|' + movement.batch_id;
    totals.set(key, round3((totals.get(key) || 0) + movement.quantity_delta));
  }
  for (const batch of store.batches) {
    for (const location of [PHARMACY_LOCATION, STORE_LOCATION]) {
      const key = location + '|' + batch.id;
      if (!totals.has(key)) continue;
      const quantity = round3(totals.get(key));
      if (!seen.has(key) && quantity > 0) {
        seen.add(key);
        balances.push({
          id: txId(), location_id: location, batch_id: batch.id,
          quantity_on_hand: quantity, quantity_reserved: 0, version: 0,
          updated_at: NOW
        });
      }
    }
  }
  store.finalBalances = new Map();
  for (const row of balances) store.finalBalances.set(row.location_id + '|' + row.batch_id, row.quantity_on_hand);
  return balances;
}

function insertProcurement(db, ctx) {
  const store = ctx.inventory;
  db.insert('medication_batches',
    ['id', 'medication_id', 'batch_number', 'manufacturer', 'manufactured_on', 'expires_on', 'created_at'],
    store.batches, 400);
  db.insert('purchase_orders',
    ['id', 'branch_id', 'supplier_id', 'order_number', 'order_status', 'currency_code', 'subtotal',
      'discount_amount', 'tax_amount', 'total_amount', 'requested_by', 'approved_by', 'ordered_at',
      'expected_delivery_on', 'notes', 'created_at', 'updated_at'],
    store.purchaseOrders, 300);
  db.insert('purchase_order_items',
    ['id', 'purchase_order_id', 'line_number', 'medication_id', 'item_description_snapshot',
      'ordered_quantity', 'received_quantity', 'unit_of_measure', 'unit_cost', 'line_discount', 'line_total'],
    store.poItems.map((item) => Object.assign({}, item, { received_quantity: item.received || 0 })), 400);
  db.insert('purchase_order_approvals',
    ['id', 'purchase_order_id', 'approval_step', 'approver_user_id', 'decision', 'decision_note',
      'decided_at', 'created_at'], store.approvals, 400);
  db.insert('goods_receipts',
    ['id', 'branch_id', 'purchase_order_id', 'inventory_location_id', 'receipt_number',
      'supplier_delivery_reference', 'receipt_status', 'received_by', 'posted_by', 'received_at',
      'posted_at', 'notes', 'created_at'], store.goodsReceipts, 300);
  db.insert('goods_receipt_items',
    ['id', 'receipt_id', 'purchase_order_id', 'purchase_order_item_id', 'medication_id',
      'medication_batch_id', 'accepted_quantity', 'rejected_quantity', 'rejection_reason', 'created_at'],
    store.receiptItems, 400);

  const received = store.purchaseOrders.filter((order) => order.order_status === 'RECEIVED').length;
  db.log('  batches: ' + store.batches.length + ', purchase orders: ' + store.purchaseOrders.length +
    ' (' + received + ' received), receipts: ' + store.goodsReceipts.length);
}

function insertLedger(db, ctx) {
  const store = ctx.inventory;
  const balances = close(store);
  db.insert('stock_balances',
    ['id', 'location_id', 'batch_id', 'quantity_on_hand', 'quantity_reserved', 'version', 'updated_at'],
    balances, 400);
  db.insert('stock_movements',
    ['id', 'location_id', 'batch_id', 'movement_type', 'quantity_delta', 'reference_type',
      'reference_id', 'transfer_group_id', 'reason', 'performed_by', 'occurred_at'],
    store.fixed.concat(store.dispenseMovements), 400);
  db.log('  movements: ' + (store.fixed.length + store.dispenseMovements.length) +
    ', balances: ' + balances.length);
}

module.exports = {
  generate, insertProcurement, insertLedger,
  advanceTo, available, take, close, round3
};
