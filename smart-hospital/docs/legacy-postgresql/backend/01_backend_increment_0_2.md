# Backend Increment 0.2 — Identity Mapping and Branch-Scoped Access

## Summary

Increment 0.2 builds on the first Jakarta EE REST slice and closes an important authorization gap before adding more clinical workflows.

### Added
- V003 migration adds `hospital.patient_branch_access` with a unique patient/branch grant, grant actor, reason, grant time and optional revocation time.
- Development seed maps three GlassFish file-realm principals (`hospitaladmin`, `receptionist1`, `doctor1`) to active database accounts, roles and a default branch.
- `CurrentUserService` resolves the authenticated principal to an active `user_accounts` row and branch memberships.
- Branch listings and department lookups are restricted to assigned branches.
- Patient registration grants access in the authenticated user's default branch.
- Patient search, detail and demographic updates require an active branch grant.
- Appointment booking verifies the caller's branch membership and the patient's branch grant, in addition to doctor/department/branch consistency and the PostgreSQL overlap constraint.
- Audit entries now store the mapped `actor_user_id` and `branch_id`.
- The complete `/api/v1/*` surface requires GlassFish BASIC authentication in local development; method-level role checks remain in place.
- Added schema and development-seed validation scripts and a security design note.

### Security boundary

The GlassFish file realm authenticates the local development credentials. The database account only maps the authenticated principal to the hospital account, roles and branches. The seeded `password_hash` value `GLASSFISH_FILE_REALM_ONLY` is an explicit marker, not a credential hash. Production authentication, department-level and care-relationship authorization, emergency/break-glass access and denied-access audit events remain future work.

### Database application order

1. V001 core schema.
2. V002 extended hospital, procurement, analytics and accounting domains.
3. V003 patient branch access.
4. V002 and V003 schema validation.
5. V001 reference data seed.
6. V002 local development account seed.
7. Development-seed validation.

Use only a fresh disposable PostgreSQL database. The migrations are not rerunnable.

### Verification status

Source-level checks can validate XML well-formedness, OpenAPI YAML syntax, SQL table/constraint references and Java source parse. A real Maven compile, migration execution, GlassFish deployment and HTTP integration test must still be run on the Windows development machine because the current build environment does not contain Maven, GlassFish or PostgreSQL client/server tools.
