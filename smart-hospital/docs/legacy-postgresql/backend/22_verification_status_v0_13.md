# Verification Status — Smart Hospital v0.13

## Scope of this increment

Staff administration, account mapping, role catalogue, staff/account UI, stricter GlassFish role-group equality, branch-aware account lifecycle rules, and serialized last-administrator protection.

## Checks performed for this source snapshot

The following static/source checks were run against the packaged project:

| Check | Result |
|---|---:|
| Java source files parsed with the JDK compiler parser | 197 files; 0 syntax parser errors |
| TypeScript/TSX files transpiled for syntax | 21 files; 0 syntax diagnostics |
| XML descriptors parsed | 5 files; 0 XML parse errors |
| OpenAPI contract | 3.1.0; version 0.13.0; 71 paths; 81 schemas; 323 references; 0 missing schema references |
| SQL table and JPA entity mapping scan | 64 tables; 46 entities; 46 explicit table mappings; 0 missing mapped tables |
| Role catalogue vs GlassFish group mapping | 10 role codes; 10 mapped groups; exact set match |
| Administration contract | 7 endpoint operations represented in OpenAPI and REST resource |
| Release archive | integrity check passed |

The TypeScript check above is syntax transpilation only, not a semantic TypeScript typecheck. The Java parser check is syntax-only, not dependency compilation. The SQL/JPA scan is a source mapping cross-check, not SQL execution or ORM bootstrap.

## Not executed in this environment

- `mvn clean package` with Jakarta EE dependencies resolved.
- `npm ci`, `npm run typecheck` or `npm run build` with project dependencies installed.
- Applying V001–V010 to a live PostgreSQL instance.
- Deploying the WAR to GlassFish 8.0.5.
- Creating File Realm identities and checking role equality against a live GlassFish security context.
- Browser tests for staff/account workflows.
- Full security, audit, concurrency, JTA/JMS and end-to-end regression tests.

Do not treat v0.13 as runtime-certified or production-ready. The full integration and acceptance test plan remains a final project gate, as requested by the project owner.
