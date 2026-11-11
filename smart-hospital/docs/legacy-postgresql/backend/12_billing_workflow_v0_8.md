# Billing and Payment Workflow — v0.8

## Scope
This increment implements service-catalogue billing, draft invoices, invoice issue, payment posting and outstanding-balance calculation. It uses the existing `service_catalog`, `invoices`, `invoice_items` and `payments` tables. V007 adds database safeguards and payment idempotency fields; no new tables are created.

## API
- `POST /api/v1/billing/invoices` — create draft invoice for an encounter.
- `GET /api/v1/billing/invoices?branchId={uuid}&status=ISSUED&limit=50` — list recent branch invoices.
- `GET /api/v1/billing/invoices/{invoiceId}` — invoice details and payments.
- `POST /api/v1/billing/invoices/{invoiceId}/issue` — issue a validated draft.
- `POST /api/v1/billing/invoices/{invoiceId}/payments` — post a payment.

## Draft invoice creation
Only `HOSPITAL_ADMIN`, `HOSPITAL_MANAGER` and `ACCOUNTANT` can create or view invoices in this increment. The service checks branch membership, active branch, encounter-to-branch matching and patient-to-branch access. The patient is derived from the encounter. Client-provided prices are not accepted: each line uses the active `service_catalog.standard_price` and currency. All lines must use the branch default currency. Repeated service IDs in one request are rejected; clients should combine their quantities.

The current implementation creates service-catalogue lines only, with discount and tax amounts set to zero. Medication-dispense charge integration is deferred because the v0.7 medication/dispense schema does not yet contain a reviewed sale-price model. A `ROOM-GEN` example service is seeded for demonstration, but room charges are not automatically accrued by length of stay.

## Issue workflow
A draft invoice must have at least one line. The service recalculates subtotal and total, then transitions the invoice to `ISSUED`. V007 adds a database trigger that rechecks the sum of line totals and rejects item mutations after issue. Invoice financial fields are immutable once the invoice leaves `DRAFT`.

## Payment workflow
Payment posting is permitted only for `ISSUED` or `PARTIALLY_PAID` invoices. The invoice is pessimistically locked. The service computes the posted amount and outstanding balance, rejects overpayment, creates a server-generated receipt number and updates invoice status to `PARTIALLY_PAID` or `PAID`.

Each payment request must include an idempotency key between 16 and 80 characters. A SHA-256 fingerprint binds that key to invoice, amount, method and transaction reference. Repeating the same key and same payload returns the existing invoice without posting a second payment; reusing the key with a different payload is rejected. V007 adds a unique key index, a database trigger that rechecks invoice status/currency/outstanding balance under an invoice-row lock, and an immutability trigger for payment receipts.

## Accounting and financial controls
This increment records invoice totals and payment receipts but does not yet generate automatic general-ledger journal entries. Although the chart of accounts, fiscal periods and journal tables already exist from V002, integration between invoice/payment posting and the double-entry ledger is a later increment. Refunds, voids, credit notes, insurance claims, multi-currency conversion and tax calculation are not implemented here.

## API/DB consistency
The service flushes a new payment before changing invoice status so the database transition trigger can verify the posted-payment sum. The invoice lock serializes concurrent payment attempts for that invoice; the database trigger independently prevents overpayment and currency mismatch. All monetary values use `BigDecimal` and two-decimal currency precision.

## Development data
V007 seeds synthetic ward/room/bed fixtures, sample service catalogue rows in YER and the `accountant1` database role mapping. The matching GlassFish file-realm user must be created separately as described in `backend/README.md`. Seed rows are not real service prices or medical tariffs.
