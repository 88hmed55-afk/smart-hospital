# Verification Status — Smart Hospital v0.14

## Increment scope

Operational settings API/UI, branch-scoped audit browsing, authenticated-user notification inbox, append-only audit migration V011, OpenAPI contract and documentation updates.

## Checks executed in this source package

| Check | Result | Scope / limitation |
|---|---:|---|
| Java source parser (`JavacTask.parse`, Java 21) | 208 files; 0 parse errors | Syntax parsing only; no Jakarta EE dependency compilation or type resolution |
| TypeScript/TSX `transpileModule` syntax pass | 24 files; 0 syntax errors | Syntax transpilation only; not semantic `tsc --noEmit` |
| XML parsing | 4 files; 0 parse errors | Well-formedness only; server descriptor validation not executed |
| OpenAPI YAML and local schema references | OpenAPI 3.1.0; version 0.14.0; 77 paths; 88 schemas; 143 schema references; 0 missing references | Contract structure and local schema-ref check only; no live endpoint conformance tests |
| SQL migration inventory | V001–V011; 64 distinct `CREATE TABLE` statements | Static inventory only; migrations were not applied to PostgreSQL |
| Explicit JPA `@Table` declarations | 46 declarations; 0 mapped tables missing from migration inventory | Name-presence check only; no runtime ORM mapping validation |
| Security role descriptor comparison | 10 declared roles; all 10 GlassFish role/group mappings match | XML/content comparison only; no GlassFish File Realm authentication test |
| Audit mutation review | No application UPDATE/DELETE/TRUNCATE statements against `hospital.audit_logs` found | Static source search only; V011 triggers have not been exercised against PostgreSQL |
| Package input scan | No `.env`, keystore, certificate or log files included | File-name scan only; does not replace secret scanning |

## Not executed in this environment

- `mvn clean package` and Maven dependency/type compilation.
- `npm ci`, semantic TypeScript typecheck (`npm run typecheck`) or production build (`npm run build`).
- Applying V001–V011 to a live PostgreSQL instance, including V011 trigger and privilege tests.
- Deploying the WAR to GlassFish 8.0.5 and verifying File Realm/Jakarta Security behavior.
- Live JTA, JMS/MDB delivery, notification ownership, operational-setting authorization, and audit pagination tests.
- Browser-based end-to-end or accessibility tests.

The package remains an academic development source package using synthetic data. It is not production-certified or clinically approved. Do not claim runtime acceptance until the target Windows environment passes the full database, deployment, security, concurrency, messaging and user acceptance test plan.
