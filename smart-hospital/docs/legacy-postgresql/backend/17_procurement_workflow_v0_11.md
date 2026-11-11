# Procurement and stock receiving workflow — v0.11

## Purpose

This increment connects the supplier directory, purchase-order approval lifecycle, goods receiving and medication stock ledger. It uses the procurement tables introduced in V002 and adds a line-level received quantity plus database safeguards in V009. No additional table is introduced, so the hospital schema remains at 64 tables.

## Lifecycle

1. An inventory officer, hospital manager or hospital administrator creates a draft purchase order for one branch and one active supplier.
2. The backend resolves medication references, reads the branch currency and calculates line/header totals. The client never supplies a trusted header total or currency.
3. An authorized inventory user submits the draft.
4. A hospital manager or administrator approves or rejects the submitted purchase order. The decision is recorded in `purchase_order_approvals`.
5. An inventory user marks an approved order as sent to the supplier.
6. On delivery, an inventory user records accepted and rejected quantities per purchase-order line, chooses a main-store or pharmacy location, and records batch, manufacturer and expiry details for accepted quantities.
7. The service atomically creates the goods receipt and receipt lines, increments line-level received quantity, creates/reuses the medication batch, increases stock balances, creates receipt stock movements and updates the purchase-order status.

## REST endpoints

- `GET /api/v1/procurement/suppliers?q=&limit=`
- `POST /api/v1/procurement/suppliers`
- `GET /api/v1/procurement/locations?branchId=`
- `GET /api/v1/procurement/purchase-orders?branchId=&status=&limit=`
- `POST /api/v1/procurement/purchase-orders`
- `POST /api/v1/procurement/purchase-orders/{orderId}/submit`
- `POST /api/v1/procurement/purchase-orders/{orderId}/approval`
- `POST /api/v1/procurement/purchase-orders/{orderId}/order`
- `POST /api/v1/procurement/purchase-orders/{orderId}/receipts`

## Authorization

| Operation | Allowed roles | Additional enforcement |
|---|---|---|
| Supplier list/create | HOSPITAL_ADMIN, HOSPITAL_MANAGER, INVENTORY_OFFICER | Authenticated mapped hospital account |
| List orders/create draft/submit/mark ordered/receive | HOSPITAL_ADMIN, HOSPITAL_MANAGER, INVENTORY_OFFICER | Explicit branch membership is required |
| Approve/reject | HOSPITAL_ADMIN, HOSPITAL_MANAGER | Explicit branch membership is required |
| Medication catalogue search for purchase creation | HOSPITAL_ADMIN, HOSPITAL_MANAGER, INVENTORY_OFFICER, DOCTOR, PHARMACIST | Active medication catalogue only |

The UI role visibility is not an authorization boundary; server role annotations and service-level branch checks enforce the workflow.

## Financial and stock rules

- Purchase-order currency is copied from the selected branch.
- Duplicate medication lines are rejected in one order; combine quantities into one line.
- Quantities allow up to three decimal places; unit costs and discounts allow two.
- A line discount cannot exceed its gross line amount.
- Submitted/approved/ordered purchase-order lines are immutable except for monotonic `received_quantity` updates.
- Received quantity cannot decrease or exceed ordered quantity.
- An order can become `RECEIVED` only when every line's accepted received quantity reaches its ordered quantity. Rejected quantities do not count as received stock.
- A receipt may contain accepted and rejected quantities. Rejected quantities require a reason and never increase stock.
- Accepted quantities require a unique medication batch identifier and a future expiry date. Existing batch numbers may be reused only when manufacturer and dates match.
- Goods receipt lines are immutable after posting.
- Receipt stock movements must match a posted receipt line and accepted quantity.
- Receipt creation, batch/stock balance updates, stock movements and order status changes share one JTA transaction. A failure rolls back all parts.
- Receipt inventory locations must be active `MAIN_STORE` or `PHARMACY` locations in the purchase-order branch.

## Technical implementation

- JPA entities: `Supplier`, `PurchaseOrder`, `PurchaseOrderItem`, `GoodsReceipt`, `GoodsReceiptItem`.
- EJB service: `ProcurementService` with container-managed REQUIRED transactions.
- REST resource: `ProcurementResource` under `/procurement`.
- Stock integration: reuses `Medication`, `MedicationBatch`, `InventoryLocation`, `StockBalance` and `StockMovement` domain entities.
- Database increment: `V009__procurement_receiving_integrity.sql`.
- Development fixture: `V009__development_supplier_fixture.sql` uses a clearly synthetic supplier and `.invalid` email domain, plus a synthetic inventory officer and separate `MAIN_STORE` location for receiving demonstrations.

## UI

`frontend/src/pages/InventoryPage.tsx` now provides supplier creation, purchase-order drafting, submission, approval/rejection, marking orders as sent and goods receipt entry. The receipt screen requires batch and expiry data for accepted quantities and a rejection reason for rejected quantities.

## Limitations in this increment

- Supplier records are global master data, not branch-specific.
- Purchase-order tax is currently zero in the workflow; jurisdiction-specific tax configuration is not yet implemented.
- Multi-step approval matrices, purchase returns, supplier invoices, landed costs and journal-entry integration for procurement are future increments.
- Receiving is posted atomically in one request; there is no separate editable receipt draft screen yet.
- The existing inventory module covers medication stock ledger and procurement, not every non-medication consumable category.

## Acceptance scenarios prepared for the final integration test gate

| ID | Scenario | Expected result |
|---|---|---|
| PR-01 | Inventory officer creates an order with one active supplier and two distinct DEMO medication items | Draft is created; line totals and branch currency are calculated by the backend |
| PR-02 | Inventory officer submits a draft | Status moves to `SUBMITTED`; lines become immutable except received quantity |
| PR-03 | Manager approves a submitted order | Status moves to `APPROVED`; approval actor and decision are recorded |
| PR-04 | Inventory officer attempts approval | HTTP 403; no approval row or status change |
| PR-05 | Inventory officer marks approved order as ordered | Status moves to `ORDERED` and `ordered_at` is recorded |
| PR-06 | Receiving quantity exceeds remaining order quantity | Request is rejected; no receipt, stock balance, movement or received-quantity mutation is committed |
| PR-07 | Accepted quantity has no batch or future expiry | Request is rejected |
| PR-08 | Rejected quantity has no reason | Request is rejected |
| PR-09 | Accepted and rejected quantities are valid and within remainder | Receipt, batch, stock balance, stock movement and purchase-order quantity update commit together |
| PR-10 | Client repeats the same receipt HTTP request after a timeout | Client must not blindly retry: this endpoint does not yet expose receipt idempotency. This is a known follow-up item before production use |
| PR-11 | Another user receives the same purchase order concurrently | Parent purchase-order lock serializes receipt processing; remaining quantity is rechecked under lock |
| PR-12 | Inventory officer requests a branch outside assigned scope | HTTP 403; no branch-specific supplier-order/location data is returned |

These are specified acceptance cases, not executed test results. They should be run in the final integration test phase against GlassFish and PostgreSQL.
