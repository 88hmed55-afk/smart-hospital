# Verification status — v0.7

## Source-level checks prepared
The v0.7 package includes source-level validation assets for:
- OpenAPI 3.1 syntax and local schema reference resolution.
- XML well-formedness.
- JAX-RS endpoint inventory and endpoint-to-service method matching.
- Jakarta Security roles declared in `web.xml`.
- JPA entity table names against migration-created hospital tables.
- V001–V006 migration chain references and seed/validation file references.
- ZIP archive integrity.

## Runtime checks not performed in this environment
The following have **not** been executed here:
- Maven dependency resolution or `mvn clean package`.
- PostgreSQL V001–V006 execution or schema validation against a live database.
- GlassFish deployment or REST endpoint execution.
- GlassFish file-realm login verification.
- JMS broker/queue/MDB delivery verification.
- Database locking, rollback, idempotency race and FEFO runtime tests.
- React browser integration or end-to-end workflow tests.

The available environment does not include Maven, `psql`, a PostgreSQL server or GlassFish. Java 21 and static parsers are available. Therefore the project is **not represented as compiled, deployed, or runtime-tested**.

## Final acceptance gate
The user intends to test the complete system after implementation is finished. Before that handoff, the project still needs:
1. Billing/payment posting and reconciliation workflows.
2. Admission/bed assignment workflows and complete inventory/procurement receiving.
3. General-ledger posting and accounting close/reversal.
4. Analytics/decision-card generation and audit review UI.
5. Complete React application, Arabic RTL layouts and role-based navigation.
6. Full Maven build, PostgreSQL fresh/upgrade migration runs, GlassFish deployment, JMS verification, automated tests and browser end-to-end tests in a real local environment.

Static source consistency must not be confused with runtime acceptance.
