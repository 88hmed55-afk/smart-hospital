# Verification Status — Smart Hospital v0.15

## Increment scope

Adds an authenticated liveness/readiness distinction. `/health` reports process liveness. `/health/ready` performs read-only probes of the configured PostgreSQL datasource and JMS connection factory and returns HTTP 200 (`UP`) or HTTP 503 (`DEGRADED`). No SQL migration is added; schema remains V001–V011 with 64 tables.

## Source checks performed

| Check | Result | Limitation |
|---|---:|---|
| Java source parser (`JavacTask.parse`, Java 21) | 210 files; 0 parse errors | Parser only; not Maven dependency/type compilation |
| TypeScript/TSX syntax transpilation | 24 files; 0 syntax errors | Not semantic `tsc --noEmit` |
| XML descriptor parsing | 4 files; 0 parse errors | XML well-formedness only |
| OpenAPI contract parse and local schema refs | OpenAPI 3.1.0; API 0.15.0; 78 paths; 89 schemas; 145 schema refs; 0 missing refs | Not live HTTP conformance |
| Migration/table inventory | 11 migrations; 64 CREATE TABLE statements; 64 distinct tables | No live database migration performed |
| JNDI/resource alignment | Readiness service uses the same `jdbc/SmartHospitalDS` and `java:app/jms/SmartHospitalConnectionFactory` names as persistence/JMS configuration; 46 JPA table mappings all exist in migration inventory | JNDI lookup and broker connection not exercised on GlassFish |
| Security role alignment | All 10 roles declared in web.xml, glassfish-web.xml and the readiness resource match | No live File Realm test |

The TypeScript check above uses the compiler transpilation API to detect syntax diagnostics; it is not a typecheck of imported React/Query dependencies. Java parsing uses the JDK parser and does not resolve Jakarta EE APIs.

## Not executed in this environment

- Maven dependency resolution and WAR package build.
- npm dependency installation, semantic TypeScript typecheck or production bundle.
- PostgreSQL connectivity and V001–V011 execution.
- GlassFish deployment, JDBC pool/JNDI datasource resolution or GlassFish JMS resource resolution.
- Live `GET /health/ready` checks returning HTTP 200/503 under healthy and failed dependency conditions.
- End-to-end browser, authorization, concurrency, JTA and JMS/MDB tests.

This remains an academic development package with synthetic data only. It is not clinically approved or production-certified. Readiness source code is present, but runtime readiness has not been verified until it is deployed against the target PostgreSQL and GlassFish installation.
