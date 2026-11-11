# Verification Status — Smart Hospital v0.12

## Source-level checks performed
- Java grammar parsing: 188 Java source files parsed with zero parser diagnostics (this is not a dependency/type compilation).
- TypeScript/TSX syntax transpilation: 20 source files with zero syntax diagnostics (this does not resolve npm packages or prove semantic type correctness).
- OpenAPI 3.1 YAML parsed successfully: 66 paths, 74 schemas and zero unresolved local schema references.
- XML parsing succeeded for the Maven POM, persistence configuration, web descriptor, GlassFish descriptor and CDI descriptor.
- Static JPA table-name check: all 46 explicitly mapped entity tables exist among the 64 tables created by V001–V010.
- Added 4 accounting JPA entity classes, 4 accounting DTO classes, 1 EJB service and 1 JAX-RS resource.
- Wired invoice issuance and payment capture to automatic journal posting through an injected EJB using REQUIRED transaction semantics.
- Added a database migration guard to reject journal lines against inactive, control or non-postable accounts.
- Added development chart-of-account seed, current/next fiscal-year periods and synthetic manager identity.
- Added SQL seed validation for required accounts, fiscal periods and trigger presence.
- Added accounting endpoints and schemas to the OpenAPI 3.1 contract, including journal-detail GET.
- Added an Arabic RTL React general-ledger workspace with accounts, trial balance, journal listing, manual draft creation, posting and reversal-draft actions.
- Added a v0.11-to-v0.12 PowerShell database upgrade script and updated fresh-install migration order.

## Not executed in this environment
- Maven dependency resolution and `mvn clean package`.
- PostgreSQL V010 migration execution and SQL validation against a live PostgreSQL server.
- GlassFish deployment, EJB injection, JTA rollback and REST authentication checks.
- Browser build and UI integration tests.
- End-to-end accounting acceptance tests.

The current environment has Java 21 but does not have Maven, PostgreSQL client/server or the frontend `node_modules` directory available. npm dependency installation previously failed due to a temporary DNS/network resolution error; the UI has therefore not been type-checked against its real React package declarations. Therefore this increment is source-reviewed but not runtime-verified. Do not represent it as a successfully compiled or deployed build until those commands are run in the target Windows environment.

## Required final acceptance scenarios
1. Issue an invoice with subtotal 1,000.00, discount 100.00 and tax 0.00. Expect Dr AR 900.00, Dr discounts 100.00, Cr service revenue 1,000.00.
2. Receive 400.00 cash against the invoice. Expect Dr cash 400.00, Cr AR 400.00, and invoice balance 500.00.
3. Retry the same payment idempotency key and payload. Expect no second payment and no second journal.
4. Attempt to post an unbalanced manual journal. Expect service rejection and database trigger protection.
5. Attempt to add a journal line to an inactive or control account. Expect database rejection.
6. Attempt to post into a closed or out-of-range fiscal period. Expect rejection and no partial changes.
7. Reverse a posted journal. Expect a draft with debit/credit sides swapped; post it separately.
8. Verify trial-balance total debits equal total credits and closing debit balances equal closing credit balances.
9. Verify a user without an accounting role receives HTTP 403.
10. Verify an accountant cannot access a branch outside the user's branch memberships.
