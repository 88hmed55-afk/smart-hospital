# Care Team and Laboratory Workflow — v0.6

## Scope
This increment implements a controlled nursing care-team assignment and the first complete laboratory workflow. It uses synthetic development fixtures only. It is not a validated clinical testing system.

## Nursing care team
A care-team assignment ties one active nurse to one active encounter. The attending doctor or a hospital manager can assign or release a nurse. A nurse must have an active `NURSE` account, an active staff record, membership in the encounter's branch, and an active user-to-branch access grant. The database stores assignment history; released assignments are not deleted. A partial unique index enforces one active primary nurse per encounter and one active assignment per nurse/encounter.

Nurses can access an encounter only while assigned to its care team. The attending doctor can access the encounter as its clinical owner. Branch access and active patient-to-branch access are checked separately. Care-team assignment is not a replacement for a comprehensive production consent and break-glass policy.

Endpoints:

- `GET /api/v1/encounters/{encounterId}/care-team`
- `POST /api/v1/encounters/{encounterId}/care-team`
- `POST /api/v1/encounters/{encounterId}/care-team/{assignmentId}/release`

Example assignment request:

```json
{
  "staffId": "<nurse-staff-uuid>",
  "careRole": "PRIMARY_NURSE"
}
```

## Laboratory workflow

1. The attending doctor searches active catalogue entries and creates an order for an active encounter.
2. The order and order items are committed in one transaction; an outbox event is written in that same transaction.
3. A scheduled EJB outbox dispatcher publishes the event to the GlassFish application JMS queue.
4. The MDB creates deduplicated notifications for active laboratory staff assigned to the same branch.
5. A laboratory technician or supervisor collects a specimen and records its specimen code.
6. A technician starts processing and records one result per ordered test item.
7. A different laboratory supervisor independently reviews and releases the result.
8. A second outbox event is sent; the MDB notifies the ordering doctor.
9. The physician's encounter endpoint exposes result values only after `released_at` is set. Lab staff can view operational/unreleased results within their authorized branch.

Endpoints:

- `GET /api/v1/lab-tests?q=...&limit=...` — doctor.
- `POST /api/v1/encounters/{encounterId}/lab-orders` — attending doctor.
- `GET /api/v1/encounters/{encounterId}/lab-orders` — attending doctor; unreleased result values are hidden.
- `GET /api/v1/lab-orders?status=...&limit=...` — laboratory technician/supervisor; includes minimum patient identifiers for specimen matching.
- `POST /api/v1/lab-orders/items/{itemId}/collect` — laboratory technician/supervisor.
- `POST /api/v1/lab-orders/items/{itemId}/start-processing` — laboratory technician.
- `POST /api/v1/lab-orders/items/{itemId}/result` — laboratory technician.
- `POST /api/v1/lab-orders/items/{itemId}/review-release` — laboratory supervisor, not the performer.

Example doctor order request:

```json
{
  "priority": "ROUTINE",
  "clinicalQuestion": "Academic demo request only",
  "testIds": ["<demo-lab-test-uuid>"]
}
```

Example synthetic result:

```json
{
  "resultText": "DEMO RESULT — NOT FOR CLINICAL USE",
  "resultNumeric": null,
  "resultUnit": "demo-unit",
  "referenceRange": null,
  "abnormalFlag": "UNKNOWN"
}
```

The development seed uses `DEMO-LAB-*` catalogue codes, non-clinical units and no reference ranges. Do not replace these with clinical catalogues without review by qualified laboratory professionals and validation of local standards.

## Transaction and messaging guarantees
- Creating an order and writing its outbox row use the same JTA transaction.
- Publishing may occur more than once if an ambiguous broker outcome happens; notification insertion is idempotent using `(source_event_id, recipient_user_id)`.
- MDB processing uses container-managed transaction behavior. Broker redelivery and dead-letter configuration must be verified in the target GlassFish domain.
- A messaging failure must not roll back a committed clinical result; it leaves the outbox event pending for another scheduled attempt.
- The current dispatcher does not yet implement a configurable terminal retry threshold and operational replay UI. That is a follow-up hardening item.

## Authorization matrix

| Action | Doctor (attending) | Nurse (assigned) | Lab technician | Lab supervisor |
|---|---:|---:|---:|---:|
| Search lab catalogue | Yes | No | No | No |
| Create lab order | Yes | No | No | No |
| View order details | Own encounter; released values only | No | Branch | Branch |
| Collect specimen | No | No | Yes | Yes |
| Start processing | No | No | Yes | No |
| Record result | No | No | Yes | No |
| Review and release | No | No | No | Yes (different person) |
| View care team | Yes | Assigned encounter | No | No |
| Assign/release nurse | Attending doctor | No | No | No |

Hospital managers can assign/release care-team members but do not receive clinical encounter read access from that permission alone.

## Synthetic identities
- `labtech1` — `LAB_TECHNICIAN`
- `labsupervisor1` — `LAB_SUPERVISOR`

Create corresponding GlassFish file-realm users as documented in `backend/README.md`. The database seed's `password_hash` values are markers, not credentials.
