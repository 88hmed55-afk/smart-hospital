# Smart Hospital v0.4 — Verification Status

## Checks completed in the packaging environment

| Check | Result | Scope |
|---|---|---|
| Java parser | PASS | 41 Java source files parsed with JDK 21; zero syntax errors. This is parsing only, not dependency-aware compilation. |
| XML parsing | PASS | 5 XML files are well-formed. |
| OpenAPI document | PASS | YAML parses as OpenAPI 3.1.0 and exposes 7 paths with global HTTP Basic security. |
| Package layout | PASS | Java package declarations match source folder paths. |
| Migration table counts | PASS | V001 adds 41, V002 adds 20, V003 adds 1; total 62. |
| JPA table mapping | PASS | All 9 mapped JPA tables exist in the SQL migrations. |
| Security role names | PASS | All role strings used in `@RolesAllowed` are declared in `web.xml`. |
| Archive integrity | Verified during packaging | ZIP is generated and checked at packaging time. |

## Checks not performed here

- Maven dependency resolution and `mvn clean package` compilation.
- Applying V001–V003 against a live PostgreSQL instance.
- Running the PostgreSQL validation scripts against an actual database.
- Installing the PostgreSQL JDBC driver in GlassFish and pinging `jdbc/SmartHospitalDS`.
- Deploying the WAR to GlassFish 8.0.5 and executing authenticated HTTP requests.
- Load, concurrency, and security penetration tests.

The environment used to prepare this bundle has JDK 21 but does not have Maven, PostgreSQL (`psql`) or GlassFish installed. External dependency retrieval is unavailable here. Do not interpret syntax/static checks as a successful build or deployment.

## Required local verification on Windows

Follow `docs/backend/00_دليل_تشغيل_النسخة_الأولى.md`, then:

1. Run `mvn clean package` from `backend/`.
2. Verify `ping-connection-pool SmartHospitalPool` succeeds.
3. Deploy `target/smart-hospital-api.war` to GlassFish.
4. Use the sample GlassFish accounts and `docs/backend/02_api_examples.md` to exercise health, branches, patient registration/search/detail and appointment booking.
5. Confirm a second overlapping appointment returns HTTP 409.
6. Confirm a user without a matching account/role/branch mapping receives HTTP 403.

Use synthetic test data only. This increment is not for clinical deployment.
