# Pharmacy and prescription workflow — v0.7

## Scope
This increment adds a controlled academic workflow for physician prescriptions and pharmacy dispensing. It is not a clinical prescribing reference and the development catalogue contains synthetic DEMO items only. Controlled medicines are intentionally excluded from this version.

## Roles and access boundaries
- `DOCTOR`: only the active attending doctor can create or list prescriptions for their encounter.
- `PHARMACIST`: must have an active staff profile in an active `PHARMACY` department, a `PHARMACIST` role, and branch membership. The pharmacist can list the active branch queue, pharmacy locations and stock.
- All patient-linked operations validate the current user's patient-to-branch grant.
- GlassFish file-realm identities are for isolated local development only.

## Prescription lifecycle
1. The doctor selects an active medication catalogue item.
2. The doctor creates a prescription against an active encounter.
3. Each medication can appear only once in a prescription; quantities are positive `NUMERIC(14,3)` values.
4. Controlled medicines are rejected by the current academic implementation.
5. The prescription is committed with audit metadata in the same EJB transaction.

## Dispensing lifecycle
1. The pharmacist selects a pharmacy location in their authorized branch.
2. The pharmacist selects an active or partially dispensed prescription.
3. The backend locks the prescription row and selected prescription-item rows.
4. The backend checks remaining prescribed quantities.
5. Eligible stock balances are locked and allocated by earliest expiry date (FEFO), then stable batch ID.
6. Each allocation decreases the on-hand quantity, creates a dispense line and writes a negative stock movement.
7. The dispense header is posted only after allocation rows and matching stock ledger rows have flushed successfully.
8. The prescription status is recomputed from posted dispense quantities.

All steps execute inside one container-managed transaction. An insufficient-stock error or any persistence error rolls back the dispense, stock balances and ledger movements together.

## Concurrency and idempotency
- `PESSIMISTIC_WRITE` locks serialize concurrent dispensing requests for one prescription and stock balances.
- `@Version` provides an additional optimistic version check on stock balances.
- `Idempotency-Key` is mandatory (16–80 safe characters). A stored SHA-256 request fingerprint binds the key to prescription, location and sorted item quantities.
- Repeating a committed request with the same key and same payload returns the existing dispense. Reusing a key with a different payload returns `409 Conflict`.
- A unique partial index handles concurrent duplicate keys. A concurrent race may return a conflict; retrying with the same key after the first transaction commits resolves to the original dispense.

## Database protections
V006 adds:
- One medication line per prescription.
- Idempotency key/fingerprint columns and unique key index.
- A dispense header trigger that validates active prescription, patient branch grant, pharmacy location, active pharmacist and presence of allocation rows.
- A dispense item trigger that validates header state, prescription-item/medication/batch consistency, non-expired batch, stock-balance existence and prescribed quantity ceiling.
- A posting guard that requires each allocation to have a matching negative stock ledger movement.
- Supporting indexes for pharmacy queues and stock lookups.

## REST endpoints
- `GET /medications?q=&limit=` — doctor and pharmacist catalogue search.
- `GET /encounters/{encounterId}/prescriptions` — attending doctor only.
- `POST /encounters/{encounterId}/prescriptions` — attending doctor only.
- `GET /pharmacy/locations` — active pharmacy locations in the pharmacist's default branch.
- `GET /pharmacy/stock?q=&locationId=&limit=` — available unexpired stock; location filter is optional.
- `GET /pharmacy/prescriptions?status=&limit=` — branch pharmacy queue.
- `POST /pharmacy/prescriptions/{prescriptionId}/dispenses` — requires `Idempotency-Key` header and location/items body.
- `GET /pharmacy/dispenses/{dispenseId}` — retrieve a posted branch-scoped dispense.

## Synthetic development fixtures
`V006__development_pharmacy_accounts_and_stock.sql` creates a local file-realm mapping for `pharmacist1`, one demo pharmacy location, two deliberately non-clinical DEMO medication entries, three synthetic batches and opening stock movements. It contains no real medicines, clinical reference doses or real patient data.

Create the matching local GlassFish identity:

```powershell
.\asadmin.bat create-file-user --groups PHARMACIST pharmacist1
```

## Deliberate limitations
- No clinical dosing, interaction checking, allergy decision support or controlled-drug workflows.
- No returns, reversal, void or prescription cancellation API yet. Posted dispenses are immutable; the next increment should implement a controlled reversal workflow before this is used beyond a disposable demo.
- No FEFO purchase-receiving or stock adjustment UI in this increment; synthetic opening balances seed the local pharmacy.
- Pharmacy price calculation and billing integration are still pending.
