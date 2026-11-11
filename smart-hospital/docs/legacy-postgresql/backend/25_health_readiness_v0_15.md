# Health and Dependency Readiness — v0.15

## Goal

Provide distinct liveness and readiness signals for the Jakarta EE application. Liveness indicates that the authenticated REST application is responding. Readiness checks the dependencies required by core workflows.

## Endpoints

- `GET /api/v1/health`: authenticated liveness response. Returns `status=UP`, application identifier and timestamp. It does not check PostgreSQL or JMS.
- `GET /api/v1/health/ready`: checks PostgreSQL using a read-only `SELECT 1`, then creates and starts a short-lived `JMSContext` from `java:app/jms/SmartHospitalConnectionFactory`.

## Response behavior

- HTTP 200 with `status=UP` only when both dependencies respond.
- HTTP 503 with `status=DEGRADED` when one or both dependencies are unavailable.
- The `checks` object reports only `database` and `messaging` as `UP` or `DOWN`.
- No exception text, JDBC URL, server name, credential or broker detail is returned.
- The endpoint is protected by Jakarta Security for the ten configured hospital roles, including `LAB_SUPERVISOR`.

## Operational notes

- Readiness checks do not send a test business message and do not alter hospital data.
- The JMS check validates that the configured connection factory can create and start a context. It does not prove that the MDB has consumed a message or that every queue destination is healthy.
- A future deployment can expose these endpoints to a trusted reverse proxy/monitor using a dedicated health-check identity. Do not make diagnostic dependency details public.
- This increment adds no database migration and does not alter existing hospital data.

## Verification boundary

Source-level checks are documented in `26_verification_status_v0_15.md`. PostgreSQL connectivity, GlassFish JNDI resource resolution, JMS broker connection and HTTP 200/503 behavior require live runtime verification and have not been executed in this build environment.
