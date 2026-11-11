# Smart Hospital v0.5 — Verification Status

## Static checks performed in the packaging environment

| Check | Result | Scope |
|---|---|---|
| OpenAPI parsing | PASS | YAML parses as OpenAPI 3.1.0; local `$ref` targets resolve. |
| OpenAPI path inventory | PASS | 19 REST paths represented in the current contract. |
| XML parsing | PASS | Existing XML configuration files remain well-formed. |
| Java parser diagnostics | No syntax diagnostics observed | JDK 21 `javac` parse attempt reported missing Jakarta EE dependency packages; this is not a successful compilation. |
| Source package layout | PASS | Package declarations and source directory paths were checked for the added clinical module. |
| JPA table mapping | PASS (static name check) | The additional five clinical entities map to tables defined in V001. |
| Database migration structure | Static review only | V004 adds constraints, indexes and triggers; no additional tables. |
| Archive integrity | Pending final package check | Verify generated ZIP after all files are added. |

## Checks not performed

- `mvn clean package` with Jakarta EE dependencies resolved.
- Applying V001–V004 to an actual PostgreSQL instance.
- Running PostgreSQL validation scripts, trigger tests, lock/race tests or seed validation.
- Installing the PostgreSQL JDBC driver in GlassFish and testing `jdbc/SmartHospitalDS`.
- Deploying the WAR to GlassFish 8.0.5.
- Authenticated HTTP end-to-end workflow tests.
- Load, accessibility, concurrency, privacy and security penetration tests.

The packaging environment has JDK 21 but does not have Maven, PostgreSQL (`psql`) or GlassFish installed. Do not interpret source parsing or OpenAPI checks as a successful backend build, migration or deployment.

## Local verification gate on Windows

1. For a new disposable database, run `database/apply-local.ps1`. For an existing v0.4 database, run `database/upgrade-v0.4-to-v0.5.ps1`.
2. Create matching GlassFish file-realm users for `receptionist1`, `doctor1` and `nurse1` with groups `RECEPTIONIST`, `DOCTOR` and `NURSE` respectively.
3. Configure `jdbc/SmartHospitalDS`, ping the pool, and deploy `target/smart-hospital-api.war`.
4. Follow `docs/backend/06_clinical_workflow_v0_3.md` with synthetic data only.
5. Verify a nurse cannot open an encounter, cannot create an `ASSESSMENT` note and cannot add a diagnosis.
6. Verify a doctor cannot modify or delete a signed note and cannot complete an encounter while draft notes remain.
7. Verify cross-branch patient/doctor/department combinations are rejected.
8. Verify a non-checked-in linked appointment is rejected and a completed encounter completes its linked appointment in one transaction.
9. Verify at most one primary diagnosis can be created during concurrent requests.

This increment is not for clinical deployment. Complete all runtime, integration and security checks before considering any non-academic use.
