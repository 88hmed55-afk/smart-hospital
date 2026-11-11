# General Ledger and Accounting Integration — v0.12

## Scope
This increment activates the general-ledger foundation already defined in V002. It adds the accounting JPA model, EJB service, REST resources, a React workspace, default development chart-of-account data, fiscal periods, and a database-side posting eligibility trigger.

All amounts are in the branch's configured base currency. Foreign-currency ledgers, FX revaluation, tax configuration, supplier invoice matching, payroll, and automatic inventory/COGS journalization are not implemented in this increment.

## Default development accounts
| Code | Account | Type | Normal balance | Use |
|---|---|---|---|---|
| 1000 | Cash on Hand | ASSET | DEBIT | Cash receipts |
| 1010 | Bank and Electronic Receipts | ASSET | DEBIT | Card, bank, insurance and other non-cash receipts |
| 1100 | Accounts Receivable | ASSET | DEBIT | Patient invoice balances |
| 1200 | Pharmacy Inventory | ASSET | DEBIT | Reserved for future stock valuation posting |
| 2100 | Goods Received Not Invoiced | LIABILITY | CREDIT | Reserved for receiving accruals |
| 2150 | Accounts Payable | LIABILITY | CREDIT | Reserved for supplier invoices |
| 2200 | Tax Payable | LIABILITY | CREDIT | Invoice tax component |
| 3000 | Opening Equity | EQUITY | CREDIT | Opening balances |
| 4100 | Clinical Service Revenue | REVENUE | CREDIT | Service invoice subtotal |
| 4200 | Discounts Allowed | EXPENSE | DEBIT | Invoice discount component |
| 5100 | Pharmacy Cost of Goods Sold | EXPENSE | DEBIT | Reserved for future dispensing valuation |
| 5200 | Operating Expenses | EXPENSE | DEBIT | Manual operating journals |

On the stock demo branch, development seeding also creates a synthetic Hospital Manager identity (`manager1`) for approval and oversight demos; custom branches do not receive this fixed demo identity. Development seeding creates the current and following calendar-year fiscal periods for each active branch. This is a development default, not a substitute for finance-administered fiscal-period setup in a production installation.

## Automatic posting rules
### Invoice issue
When a non-zero invoice is issued, the BillingService and AccountingService share one JTA transaction. The accounting service writes and posts:

- Debit 1100 Accounts Receivable: invoice total.
- Debit 4200 Discounts Allowed: discount amount, when non-zero.
- Credit 4100 Clinical Service Revenue: subtotal, when non-zero.
- Credit 2200 Tax Payable: tax amount, when non-zero.

The entry is posted only when the debits equal credits and an open fiscal period covers the invoice issue date. A zero-total invoice is allowed but does not generate a journal entry in this increment.

### Payment receipt
A posted patient payment generates:

- Debit 1000 Cash on Hand for CASH, otherwise debit 1010 Bank and Electronic Receipts.
- Credit 1100 Accounts Receivable for the payment amount.

The payment, invoice balance/status and journal entry are committed or rolled back together. The existing payment idempotency key and source-reference uniqueness prevent duplicate financial posting.

## Manual journal lifecycle
1. An accountant or authorized manager creates a balanced draft with 2–50 lines.
2. The service validates branch membership, finance-staff scope for accountants, active/postable account codes, positive line amounts and exact debit/credit balance.
3. A separate posting action locks the draft and revalidates the open period and account eligibility.
4. PostgreSQL independently rejects posting unless there are at least two lines, total debit equals total credit, the fiscal period is open and covers the entry date, posting metadata is present, and each line refers to an active postable account in the same branch.
5. Posted entries and lines are immutable. Corrections use a reversal draft with swapped debit/credit sides, which must be posted separately.

## REST API
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/accounting/accounts?branchId=...` | List active chart accounts |
| GET | `/api/v1/accounting/journals?branchId=...&fromDate=...&toDate=...` | List recent journal entries and lines |
| GET | `/api/v1/accounting/trial-balance?branchId=...&asOf=YYYY-MM-DD` | Cumulative trial balance (including inactive accounts with historical balances) |
| GET | `/api/v1/accounting/journals/{id}` | Retrieve a journal and its lines |
| POST | `/api/v1/accounting/journals` | Create a manual journal draft |
| POST | `/api/v1/accounting/journals/{id}/post` | Post a draft |
| POST | `/api/v1/accounting/journals/{id}/reversal` | Create a reversal draft |

The accounting UI is available at `/accounting` for HOSPITAL_ADMIN, HOSPITAL_MANAGER and ACCOUNTANT roles. Billing and payment collection remain in `/billing`.

## Database upgrade
- Fresh development database: `database/apply-local.ps1` now applies V001–V010 and loads the chart of accounts/fiscal periods.
- Existing v0.11 database (V001–V009, 64 tables): run `database/upgrade-v0.11-to-v0.12.ps1` once.
- V010 adds triggers/indexes only; it does not create new tables. The schema remains 64 tables.
- Do not run the fresh-install script against an existing `hospital` schema.

## Known limitations
- The trial balance includes inactive chart accounts so historical posted balances are not hidden when an account is later disabled. The initial ledger is single base currency per branch. No multi-currency, FX gains/losses, period closing workflow, bank reconciliation or financial statements are included yet.
- Procurement receiving and medication dispensing do not yet create inventory valuation journals. Their operational stock ledger remains authoritative until valuation rules and cost layers are implemented.
- Supplier invoices, AP settlement and payroll are not yet integrated.
- Zero-value invoices do not create journal entries.
- Development account codes are seeded defaults. Branch onboarding must provision an equivalent chart and fiscal periods before financial activity is enabled.
