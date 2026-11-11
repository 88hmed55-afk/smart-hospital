# Verification Status — Smart Hospital v0.8

## Static/source checks performed for this increment
- OpenAPI document parses as YAML/OpenAPI 3.1.0.
- All local `#/components/...` references in OpenAPI resolve.
- New admission and billing paths are present in the contract.
- New Java source files are present in the backend module tree.
- New entity table mappings are checked against the V001/V002 schema definitions.
- V007 includes invoice arithmetic, immutable issued-line behaviour, guarded invoice state transitions, payment idempotency, currency and outstanding-balance checks, and payment receipt immutability.
- Fresh-database and v0.7-to-v0.8 scripts include the V007 validation and synthetic fixtures.
- The archive integrity and package inventory are checked before delivery.

## Not executed in this environment
- Maven dependency compilation (`mvn clean package`). Maven is not installed in the current working container.
- PostgreSQL migration execution or SQL integration tests (`psql`/PostgreSQL server are not installed here).
- GlassFish deployment and application-server security realm tests.
- Runtime JPA/EJB/JTA, JMS, concurrency and REST integration tests.
- Browser-based React integration and end-to-end acceptance tests.

Therefore v0.8 is a source-complete development increment with static consistency checks, not a runtime-verified release. The final acceptance-test phase remains intentionally deferred as requested by the project owner, but source review and basic static validation are still performed on each increment.

## Known functional limitations
- Billing currently prices service catalogue lines only; pharmacy dispense pricing is not connected.
- Invoice taxes, discounts, insurance and general-ledger journal generation are not implemented.
- Payment receipts are immutable; refund, void and reversal workflow is not implemented.
- Admission consumes existing ward/room/bed master data; master-data CRUD, housekeeping/cleaning state transitions and discharge planning are not implemented.
- Smart analytics and decision cards are still a subsequent implementation increment.
- React frontend has not yet been created; this increment continues backend and database development.

## Final test gate when implementation is complete
Run all migrations on a clean PostgreSQL database, run the v0.7 upgrade path, package the WAR with Maven/JDK 21, deploy to GlassFish 8 Full Platform, configure the JDBC datasource/JMS resources and file-realm users, and then execute the complete documented API, authorization, transaction, concurrency and browser acceptance suite using synthetic test records only.
