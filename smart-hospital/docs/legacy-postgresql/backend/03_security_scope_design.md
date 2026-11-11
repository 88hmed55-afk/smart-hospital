# Security and Patient Scope — Backend Increment 0.2

## Authentication path (local development)

1. GlassFish `file` realm verifies the HTTP Basic username/password.
2. `glassfish-web.xml` maps GlassFish groups to application roles.
3. `@RolesAllowed` applies method-level role authorization in REST resources and EJB services.
4. `CurrentUserService` resolves the authenticated principal name to an active `hospital.user_accounts` row.
5. The service verifies that at least one database role assignment exists in `user_roles` and matches a GlassFish caller role, then loads the user's active branch memberships from `hospital.user_branch_access`.
6. Requests without an active mapped account, a role mapping match, or an active branch grant are rejected.

The synthetic `password_hash` value `GLASSFISH_FILE_REALM_ONLY` is a marker only. It is not a password hash, is not checked by the application, and must not be used as a production credential. Production authentication and credential lifecycle require a dedicated Jakarta Security IdentityStore or an enterprise identity provider, with secure password hashing and account recovery controls.

## Branch scope

- Branch lists are restricted to `user_branch_access`.
- Department lookup requires explicit membership in the requested branch.
- Patient registration grants access to the user's default branch by inserting `patient_branch_access`.
- Patient search uses an `EXISTS` query against active patient/branch grants.
- Patient details and demographic updates require at least one active patient grant in a branch assigned to the current user.
- Appointment booking verifies the requested branch, patient grant in that exact branch, doctor branch, and department branch.
- Appointment retrieval verifies both branch membership and patient access in the appointment branch.

## Security boundaries not yet complete

- Department-level and care-relationship authorization (for example, doctor assigned to a particular encounter).
- Auditing of rejected access attempts (successful reads/writes are recorded).
- Production-grade login, password resets, MFA, account recovery, session management, and rate limiting.
- Fine-grained per-method comparison of every `@RolesAllowed` value with the database `user_roles` assignments. The development seeds intentionally keep GlassFish groups and database role assignments identical; they must remain synchronized.
- Permission administration through the database `permissions` catalog; this increment uses GlassFish role groups for coarse-grained role checks.
- Emergency/break-glass access with justification and post-event review.

Use synthetic data only. This increment is not approved for real patient records or clinical deployment.
