# Governance, Operational Settings and Notifications — v0.14

## Scope

This increment completes a first governance workspace around existing `system_settings`, `audit_logs` and `notifications` tables. It adds no new tables; the schema remains at 64 tables.

## Operational settings

The API exposes only three allow-listed numeric operational thresholds already consumed by `OperationalAnalyticsService`:

| Key | Default | Allowed range | Unit |
|---|---:|---:|---|
| `analytics.bed_occupancy.warning_percent` | 85 | 50–100 | % |
| `analytics.lab_pending.warning_count` | 20 | 1–1000 | pending items |
| `analytics.lab_tat.warning_minutes` | 240 | 30–1440 | minutes |

Value precedence is branch override, then global value, then compiled default. A branch manager can read/update only branches assigned to the authenticated account. A hospital administrator can update a global value or an authorized branch override. A generic arbitrary-JSON settings editor is intentionally not provided.

Writes use PostgreSQL `INSERT ... ON CONFLICT` against the existing partial unique indexes and are audited in the same JTA transaction. The response includes the effective value and its source.

### Endpoints

- `GET /api/v1/system/settings?branchId={uuid}`
- `PUT /api/v1/system/settings` with `{ "key": "...", "branchId": "..." | null, "value": 90 }`

The global update path requires `HOSPITAL_ADMIN`. Branch updates require `HOSPITAL_ADMIN` or `HOSPITAL_MANAGER` and explicit branch membership.

## Audit browser

`GET /api/v1/audit-logs` supports branch, `from`, `to`, `actionCode`, `resourceType`, `offset` and `limit` filters. Only `HOSPITAL_ADMIN` and `HOSPITAL_MANAGER` may browse it, and every request resolves to exactly one branch within the caller's branch membership. The response contains actor username, action/resource codes, optional resource UUID, outcome, branch and time. It does not include free-form context metadata, clinical note content, result text, patient contact details or other patient-identifying content.

Migration V011 installs `BEFORE UPDATE OR DELETE` and `BEFORE TRUNCATE` triggers on `hospital.audit_logs`, adds a branch/time index and revokes UPDATE/DELETE/TRUNCATE privileges from `PUBLIC`. This is application/database-level append-only protection; it is not a tamper-proof external archive against a database owner or superuser. A production deployment should additionally export audit events to separately controlled immutable storage and monitor privileged database access.

## Notification inbox

Notifications are always scoped to `CurrentUser.userId`; a caller cannot pass a recipient ID. The inbox supports paginated list, unread count, mark-one-read and mark-all-read. Updating a notification checks ownership in the SQL `WHERE` clause. Existing laboratory JMS/MDB producers create the notifications using event-recipient uniqueness; this increment does not add new notification producers or external email/SMS delivery.

### Endpoints

- `GET /api/v1/notifications?unreadOnly=false&offset=0&limit=20`
- `GET /api/v1/notifications/unread-count`
- `POST /api/v1/notifications/{notificationId}/read`
- `POST /api/v1/notifications/read-all`

## Frontend

- `/settings`: operational threshold editor with branch/global scope, effective-value source, allowed range and save feedback.
- `/audit`: branch-scoped audit browser with action/resource filters and pagination.
- Top-bar notification bell: unread badge, recent inbox entries, mark-one-read and mark-all-read.

The frontend hides governance routes from users without the relevant roles, while backend resource and EJB security remain authoritative.

## Data and safety boundaries

- No clinical thresholds, diagnosis or treatment decisions are configured here.
- Decision support remains deterministic operational threshold reporting.
- Notification content is generic; do not put diagnosis text, result values, full patient names, contact details or medical note text into notification bodies.
- Audit rows should remain append-only. Corrections require a new audit event, not mutation of historical rows.
