# Verification Status — Smart Hospital v0.6

## What was checked in this workspace
- File tree and Java source/package layout were inspected.
- `pom.xml`, `persistence.xml`, `web.xml` and other XML files were parsed for well-formedness.
- OpenAPI YAML was parsed and its internal `$ref` pointers and documented route-to-resource coverage were checked with a local parser where available.
- SQL migration and seed files were reviewed for table/column/constraint references and consistent migration ordering.
- Role names declared in `@RolesAllowed` were compared with roles in `web.xml`.
- ZIP archive integrity and required deliverables were checked before packaging.

## Not run — do not interpret as passed
- Maven dependency compilation (`mvn clean package`). Maven is not installed in this execution environment.
- PostgreSQL migration execution or schema validation against a real PostgreSQL server. `psql`/PostgreSQL server are not available here.
- GlassFish deployment or JNDI/JMS broker verification. GlassFish is not installed here.
- Browser/UI, authentication, authorization, cross-branch denial, concurrency, JMS redelivery, database rollback or end-to-end workflow tests.

## Required final test gate after remaining modules are implemented
1. Build backend with Maven using JDK 21 and Jakarta EE 11 Full Platform target.
2. Apply V001–V005 to a disposable PostgreSQL database; run every validation script and inspect seed rows.
3. Configure GlassFish JDBC pool/resource and create file-realm demo users.
4. Deploy WAR and verify declared JMS connection factory and queue. Confirm timer outbox publishing and MDB notifications.
5. Test every role against both allowed and denied endpoints, including branch and patient scope.
6. Run concurrent appointment booking, care-team primary-nurse assignment, specimen/result writes, stock dispensing and invoice posting scenarios.
7. Verify all transaction rollback and idempotency cases.
8. Run frontend unit, component, accessibility and browser end-to-end tests after React is implemented.
9. Restore a backup in a separate database and verify recovery.

## Known v0.6 limitations
- Backend slices are not complete hospital functionality; pharmacy dispensing, billing workflows, admissions, HR, inventory purchasing, accounting posting and the React app remain to be implemented.
- File-realm auth is development-only.
- Development lab catalogue/fixtures are synthetic and non-clinical.
- The scheduled outbox dispatcher needs configured broker monitoring, retry threshold and support/replay operations before production use.
- The API health endpoint is not a full dependency readiness check.

No claim of successful runtime compilation, migration, deployment or acceptance testing is made by this report.
