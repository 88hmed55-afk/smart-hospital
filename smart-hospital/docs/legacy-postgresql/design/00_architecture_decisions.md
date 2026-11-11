# Architecture Decision Records — Smart Hospital

**Document version:** 0.1  
**Status:** Proposed baseline for review  
**Date:** 2026-11-08

## ADR-001 — Modular monolith for the first product increment

**Decision:** Build one deployable Jakarta EE application, separated into business modules and package boundaries.

**Reasoning:** The first version is an academic system with tightly related workflows such as encounter → lab order → result → notification, and prescription → dispense → stock movement → billing. A modular monolith keeps local transactions straightforward and reduces deployment and debugging overhead while retaining clear module boundaries.

**Consequence:** Modules must not access another module's tables or repositories arbitrarily. Cross-module operations go through application services or documented domain events. Microservices are not a first-release requirement.

## ADR-002 — React SPA separated from the Jakarta EE backend

**Decision:** React + TypeScript is a separately built frontend. Jakarta REST exposes versioned JSON endpoints from GlassFish.

**Reasoning:** The frontend can evolve independently and the backend remains responsible for authorization, validation and business rules.

**Consequence:** No direct database connection from React. Use a development proxy or explicit CORS allow-list during local development. In production, prefer same-origin routing or a tightly controlled HTTPS origin.

## ADR-003 — GlassFish 8.0.5 Full Platform as the application runtime

**Decision:** Target GlassFish 8.0.5 Full Platform and JDK 21 for local development.

**Reasoning:** This runtime implements Jakarta EE 11. The project uses full EJB features, MDB and JMS. The Full Platform distribution includes the full EJB feature set and JMS resources; Web Profile does not.

**Consequence:** Use Jakarta APIs provided by the server (`provided` scope in Maven). Do not package duplicate Jakarta EE API implementations in the WAR. Keep implementation versions aligned with the chosen runtime.

## ADR-004 — PostgreSQL as the relational system of record

**Decision:** Store patient, clinical, appointment, laboratory, medication, stock, invoice, payment, user and audit metadata in PostgreSQL.

**Reasoning:** The workflows have strong relational integrity requirements and need constraints, transactions, indexes and reporting queries.

**Consequence:** Use UUID identifiers, foreign keys, check constraints, unique constraints, `TIMESTAMPTZ` for instants and `NUMERIC` for amounts/quantities. JPA entities map to the `hospital` schema (`@Table(schema = "hospital")`).

## ADR-005 — Transactional outbox for reliable domain notifications

**Decision:** Business transactions insert an event into `message_outbox` in the same PostgreSQL transaction as the state change. A publisher subsequently sends the event to JMS; MDB consumers process it idempotently.

**Reasoning:** Sending to JMS after a database commit without an outbox can lose notifications if the application fails between the two operations. The outbox makes the intent to publish durable with the database change.

**Consequence:** Consumers need idempotency keys and retry/dead-letter handling. Do not put unnecessary patient identifiers or clinical details into message payloads.

## ADR-006 — Smart features are operational decision support

**Decision:** The first smart layer uses transparent metrics, thresholds, trend analysis and explainable recommendations. Predictive models are introduced only after sufficient synthetic or authorized historical data and evaluation exist.

**Reasoning:** The project must demonstrate meaningful smart functionality without claiming to diagnose or recommend treatment.

**Consequence:** Smart cards include evidence window, source metrics, assumptions, confidence/limitations, recommendation and outcome tracking. Clinical diagnosis and treatment remain clinician decisions.

## ADR-007 — Clinical and financial history is append-oriented

**Decision:** Signed clinical notes, posted stock movements, posted payments and audit events are not hard-deleted through ordinary workflows.

**Reasoning:** These records form an accountability and traceability history.

**Consequence:** Use amendments, voids, reversals and correcting entries with actor, timestamp and reason. Apply retention policies only after formal policy is defined.

## ADR-008 — Authorization combines role and resource scope

**Decision:** Enforce roles/permissions in the backend and also verify access to the specific patient, branch, encounter or department resource.

**Reasoning:** A role such as DOCTOR or LAB_TECHNICIAN alone is not enough to grant unrestricted access to every record.

**Consequence:** React route guards are usability features only; backend authorization is mandatory. Log denied sensitive access attempts without copying protected clinical content into logs.

## ADR-009 — Single hospital first, branch-aware schema

**Decision:** Deliver a working single-hospital configuration first while retaining `branch_id` for branch-owned records and scoped user access.

**Reasoning:** This keeps the student project manageable without blocking future multi-branch expansion.

**Consequence:** Cross-branch reporting and data access must be explicitly permissioned. Do not build multi-tenant infrastructure in the first increment.

## Open design items before V002

- Bed occupancy and admission/transfer model.
- Supplier, purchase order and goods-receipt workflow.
- Imaging/radiology order and report model.
- Insurance claims and payer contracts, if required by the target hospital.
- General ledger and accounting journal model, if full accounting is in the assessed scope.
- Clinical document and attachment storage, retention and access policy.
- Smart metrics definitions, thresholds and refresh cadence.
