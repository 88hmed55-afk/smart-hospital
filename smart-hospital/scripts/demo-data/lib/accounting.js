'use strict';

/*
 * Double-entry accounting derived from billing.
 *
 * Every invoice that reached a posted state produces a receivable entry and
 * every receipt produces a cash/bank entry, exactly like
 * `AccountingService.postInvoice` / `postPayment`:
 *
 *   Invoice:   1100 Dr total, 4200 Dr discount, 4100 Cr subtotal, 2200 Cr tax
 *   Payment:   1000 Dr cash (or 1010 for any other method), 1100 Cr receivable
 *
 * Entries are inserted already POSTED, which the `ck_journal_posting_metadata`
 * check allows as long as `posted_by` and `posted_at` are present. They are
 * balanced by construction, so a later formal post would pass the MySQL
 * trigger as well.
 */

const {
  BRANCH_ID, USER, FISCAL_2026,
  txId, addMinutes, localDate
} = require('./util');

function round2(value) { return Math.round(value * 100) / 100; }

function generate(ctx) {
  const accounts = ctx.ref.accounts;
  const entries = [];
  const lines = [];
  const seq = {};
  const stats = { invoices: 0, payments: 0, skipped: 0, debits: 0 };

  function accountId(code) {
    const account = accounts[code];
    if (!account) throw new Error('Missing chart of accounts entry ' + code);
    return account.id;
  }

  function nextNumber(date) {
    const key = localDate(date).replace(/-/g, '');
    seq[key] = (seq[key] || 0) + 1;
    return 'JE-' + key + '-' + String(seq[key]).padStart(4, '0');
  }

  function post(date, description, sourceModule, sourceReferenceId, at, lineDefs) {
    const prepared = lineDefs
      .map((line) => ({
        account_id: accountId(line.account),
        debit_amount: round2(line.debit || 0),
        credit_amount: round2(line.credit || 0),
        line_memo: line.memo || null
      }))
      .filter((line) => line.debit_amount > 0 || line.credit_amount > 0);
    const debit = prepared.reduce((sum, line) => sum + line.debit_amount, 0);
    const credit = prepared.reduce((sum, line) => sum + line.credit_amount, 0);
    if (prepared.length < 2 || round2(debit) <= 0 || round2(debit) !== round2(credit)) {
      throw new Error('Unbalanced journal for ' + sourceModule + ' ' + sourceReferenceId);
    }
    const entryId = txId();
    entries.push({
      id: entryId, branch_id: BRANCH_ID, fiscal_period_id: FISCAL_2026,
      entry_number: nextNumber(date), entry_date: localDate(date),
      description, source_module: sourceModule, source_reference_id: sourceReferenceId,
      entry_status: 'POSTED', reversal_of_entry_id: null,
      created_by: USER.ACCOUNTANT, posted_by: USER.ACCOUNTANT,
      created_at: at, posted_at: at, version: 0
    });
    prepared.forEach((line, index) => {
      lines.push(Object.assign({ id: txId(), journal_entry_id: entryId, branch_id: BRANCH_ID, line_number: index + 1 }, line));
    });
    stats.debits = round2(stats.debits + debit);
  }

  // Invoices ---------------------------------------------------------------
  for (const invoice of ctx.invoices) {
    if (!invoice.issued_at || invoice.status === 'VOID' || invoice.status === 'DRAFT') {
      stats.skipped += 1;
      continue;
    }
    const total = round2(invoice.total_amount);
    if (total <= 0) { stats.skipped += 1; continue; }
    const lineDefs = [{
      account: '1100', debit: total, credit: 0,
      memo: 'ذمة مدينة - ' + invoice.invoice_number
    }];
    if (invoice.discount_amount > 0) {
      lineDefs.push({
        account: '4200', debit: round2(invoice.discount_amount), credit: 0,
        memo: 'خصم مسموح به - ' + invoice.invoice_number
      });
    }
    if (invoice.subtotal > 0) {
      lineDefs.push({
        account: '4100', debit: 0, credit: round2(invoice.subtotal),
        memo: 'إيراد خدمات - ' + invoice.invoice_number
      });
    }
    if (invoice.tax_amount > 0) {
      lineDefs.push({
        account: '2200', debit: 0, credit: round2(invoice.tax_amount),
        memo: 'ضريبة مستحقة - ' + invoice.invoice_number
      });
    }
    post(invoice.issued_at, 'إثبات فاتورة خدمات رقم ' + invoice.invoice_number,
      'BILLING_INVOICE', invoice.id, addMinutes(invoice.issued_at, 2), lineDefs);
    stats.invoices += 1;
  }

  // Payment receipts -------------------------------------------------------
  for (const payment of ctx.invoicePayments) {
    if (!payment.paid_at || payment.status !== 'POSTED') { stats.skipped += 1; continue; }
    const amount = round2(payment.amount);
    if (amount <= 0) { stats.skipped += 1; continue; }
    const debitAccount = payment.payment_method === 'CASH' ? '1000' : '1010';
    post(payment.paid_at, 'تحصيل سند قبض رقم ' + payment.receipt_number,
      'BILLING_PAYMENT', payment.id, payment.paid_at, [
        {
          account: debitAccount, debit: amount, credit: 0,
          memo: 'مقبوضات نقدية/بنكية - ' + payment.receipt_number
        },
        {
          account: '1100', debit: 0, credit: amount,
          memo: 'تسوية الذمم المدينة - ' + payment.receipt_number
        }
      ]);
    stats.payments += 1;
  }

  // Inserted chronologically so entry numbers stay readable per day.
  entries.sort((a, b) => (a.created_at.getTime() - b.created_at.getTime())
    || (a.entry_number < b.entry_number ? -1 : 1));

  ctx.journalEntries = entries;
  ctx.journalLines = lines;
  ctx.accountingStats = stats;
  return { entries, lines, stats };
}

function insert(db, ctx) {
  db.insert('journal_entries',
    ['id', 'branch_id', 'fiscal_period_id', 'entry_number', 'entry_date', 'description',
      'source_module', 'source_reference_id', 'entry_status', 'reversal_of_entry_id',
      'created_by', 'posted_by', 'created_at', 'posted_at', 'version'],
    ctx.journalEntries, 200);
  db.insert('journal_lines',
    ['id', 'journal_entry_id', 'branch_id', 'line_number', 'account_id', 'debit_amount',
      'credit_amount', 'line_memo'],
    ctx.journalLines, 400);
  const stats = ctx.accountingStats;
  db.log('  journal entries: ' + ctx.journalEntries.length + ' (invoices: ' + stats.invoices +
    ', receipts: ' + stats.payments + '), lines: ' + ctx.journalLines.length +
    ', debit total: ' + stats.debits.toFixed(2) + ' YER');
}

module.exports = { generate, insert };
