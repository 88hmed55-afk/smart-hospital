# Operational Analytics & Decision Support — v0.9

## Purpose and boundary
This increment adds explainable operational analytics for hospital management. It is not a diagnostic, triage, or treatment engine. It processes aggregate operational data only and does not expose patient names, MRNs, diagnoses, note text, or individual laboratory values in dashboard responses or decision-card evidence.

## Dashboard indicators
`GET /api/v1/analytics/dashboard?branchId=<uuid>` computes the current local calendar day using the selected branch's configured time zone and returns:
- Appointments and visits started during the branch-local day.
- Checked-in appointments.
- Active admissions and active operational bed count/occupancy.
- Laboratory items not yet reviewed/released or cancelled.
- Average time from specimen collection to result release, with a sample count.
- Number of active medication/location reorder policies at or below reorder point.
- Posted payment amount for the branch-local day, in the branch's default currency.
- Current count of open/acknowledged/in-progress decision cards.

Dashboard GET is read-only. It computes aggregates on request and does not create snapshots or decision cards.

## Metric snapshots
`POST /api/v1/analytics/dashboard/refresh?branchId=<uuid>` is restricted to hospital administrators and managers. It refreshes a daily snapshot in `metric_snapshots` for active metric definitions and creates any qualifying decision cards. Refresh can be repeated safely: a single snapshot is maintained for each metric/branch/day and a unique branch+deduplication key prevents duplicate cards for the same rule/day.

Metric windows use the branch-local calendar day converted to instants. No patient identifiers are stored in dimensions. Revenue snapshots are aggregate only.

## Configurable warning thresholds
Thresholds are stored in `hospital.system_settings` as JSON objects with a numeric `value`, and branch-specific settings override global settings:
- `analytics.bed_occupancy.warning_percent` — default 85.
- `analytics.lab_pending.warning_count` — default 20.
- `analytics.lab_tat.warning_minutes` — default 240.

Thresholds can be configured per branch by inserting/updating a `system_settings` row with the same key and that branch ID. Do not place patient data in settings.

## Inventory reorder policies
V008 creates `inventory_reorder_policies`, keyed by branch + inventory location + medication. The composite foreign key ensures the location belongs to the same branch. The available quantity is calculated as the sum of unexpired batches' on-hand quantity less reserved quantity. A medication/location policy is low-stock when available quantity is less than or equal to its reorder point.

Synthetic demo reorder policies are included only for DEMO medications and should not be interpreted as safe or clinically appropriate stock settings.

## Decision card rules
The current deterministic rules can create cards for:
- Operational bed occupancy at/above its configured warning threshold.
- Laboratory pending item count at/above its configured warning threshold.
- Average lab turnaround time at/above its configured warning threshold.
- Any active medication/location reorder policy at/below the reorder point.

Each card contains the problem statement, evidence window, observed value, threshold, explanatory analysis, recommendation, assumptions, expected operational impact, and a supporting evidence row. Confidence is intentionally null for deterministic threshold rules; the system does not fabricate a statistical confidence score.

## Decision card workflow
`GET /api/v1/analytics/decision-cards` lists cards in a branch. `PATCH /api/v1/analytics/decision-cards/{cardId}/status` accepts `ACKNOWLEDGED`, `IN_PROGRESS`, `COMPLETED`, or `DISMISSED` and checks allowed state transitions under a row lock. Only hospital administrators and managers can manage cards. Every state change is audited.

## Roles
- Dashboard read: `HOSPITAL_ADMIN`, `HOSPITAL_MANAGER`, `ACCOUNTANT`.
- Refresh and decision-card list/status: `HOSPITAL_ADMIN`, `HOSPITAL_MANAGER`.
- Branch access is independently enforced by `CurrentUserService` for every request.

## Limitations
- These are descriptive operational aggregates and threshold-based decision rules, not machine-learning predictions.
- A high bed occupancy indicator is not an assessment of clinical acuity or safe capacity.
- A laboratory backlog indicator counts workflow states and does not assess test urgency.
- A reorder alert does not create a purchase order automatically.
- Dashboard queries are not yet cached/materialized; performance must be measured against representative synthetic datasets before production use.
