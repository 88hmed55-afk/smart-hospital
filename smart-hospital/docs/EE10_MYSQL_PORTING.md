# Smart Hospital — Jakarta EE 10 / MySQL 8.0.38 Edition

## Approved target environment

| Component | Target |
|---|---|
| IDE | Apache NetBeans 21 |
| JDK | 17.0.12 |
| Application server | GlassFish 7.0.23 |
| Jakarta platform | Jakarta EE 10 — Platform / Full Platform |
| Java API dependency | `jakarta.jakartaee-api:10.0.0` (`provided`) |
| Persistence | Jakarta Persistence 3.1 |
| Persistence provider | GlassFish-bundled EclipseLink 4.x |
| Database | MySQL Server 8.0.38 / InnoDB |
| Database/schema | `hospital` |
| JDBC driver | MySQL Connector/J 9.7.0 |
| Frontend | React + TypeScript + Vite |
| Node runtime | Node.js 26.4.0 + npm |
| Backend artifact | Maven EAR `smart-hospital.ear` (WAR `smart-hospital-web.war` inside, context root `/`) |

GlassFish 7 is the Jakarta EE 10 platform line, and EclipseLink 4 was built for Jakarta EE 10 / Java 17 and Jakarta Persistence 3.1. The application uses JMS and Message-Driven Beans, therefore the GlassFish **Platform / Full Platform** distribution is required; do not use Web Profile for this project. Official references:

- <https://glassfish.org/download_gf7.html>
- <https://eclipse.dev/eclipselink/releases/4.0.html>
- <https://jakarta.ee/specifications/persistence/3.1/>

## Database installation contract

The current supported database install path is:

1. `database/mysql/01_schema.sql` — 64 base tables and core schema.
2. `database/mysql/03_constraints.sql` — generated columns and MySQL uniqueness constraints.
3. `database/mysql/04_triggers.sql` — UUID defaults and database integrity/immutability triggers.
4. `database/mysql/02_development_seed.sql` — synthetic local-development reference and workflow fixtures.
5. `database/mysql/05_validation.sql` — read-only post-install checks.

Use `database/apply-mysql-local.ps1` to run these files in this order. The script requires local MySQL client tools, uses `mysql_config_editor` for the administrator login path, and refuses a schema name other than `hospital` because entity mappings and native SQL qualify the schema. It first checks that the target schema has no table/view objects, then installs into a new/empty disposable development schema only. It does not import PostgreSQL records and does not repair partial or existing databases.

The per-version files under `database/mysql/migrations` and `database/mysql/seed` are kept for traceability and review. The supported fresh install is the aggregate path above. Do not run the old root-level PostgreSQL paths under `database/legacy-postgresql/migrations` or `database/legacy-postgresql/seed` against MySQL.

## Porting decisions and compatibility notes

- **UUID:** IDs are stored as `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`; Java uses `java.util.UUID`. Jakarta Persistence 3.1 treats UUID as a basic type and requires canonical representation when stored in `VARCHAR` or equivalent. EclipseLink 4 implements the Persistence 3.1 UUID feature.
- **Time:** the source PostgreSQL `TIMESTAMPTZ` values are represented as UTC `DATETIME(6)` in MySQL. The GlassFish JDBC connection is configured for UTC. Convert to the branch's display timezone only in the presentation layer.
- **JSON:** PostgreSQL `JSONB` becomes MySQL `JSON`. JSON defaults use MySQL 8.0.38 expression defaults such as `DEFAULT (JSON_OBJECT())` and `DEFAULT (JSON_ARRAY())`.
- **Partial unique indexes:** replaced with MySQL stored generated columns and unique indexes in `03_constraints.sql`.
- **Appointment / bed overlap:** PostgreSQL GiST exclusion constraints have no direct MySQL equivalent. Services lock the doctor/bed row and check overlap inside the same transaction; unique/current-state generated-column constraints add a second integrity layer where suitable.
- **Triggers:** PostgreSQL PL/pgSQL triggers were rewritten as MySQL triggers in `04_triggers.sql`.
- **Foreign keys:** inline `REFERENCES` syntax is not relied upon; constraints are declared at table level so MySQL enforces them.
- **Seed SQL:** PostgreSQL-specific `VALUES` table constructors, `generate_series`, `make_date`, `ON CONFLICT`, and date arithmetic were converted to MySQL 8 syntax.
- **Idempotency fingerprints:** MySQL `REGEXP_LIKE(..., 'c')` checks lowercase 64-character hexadecimal fingerprints in the dispense and payment idempotency constraints.

## GlassFish resources

- JTA datasource: `jdbc/SmartHospitalDS`
- XA driver class: `com.mysql.cj.jdbc.MysqlXADataSource`
- XA datasource interface: `javax.sql.XADataSource` (JDBC API namespace)
- JDBC pool: `SmartHospitalPool`
- Database endpoint: `127.0.0.1:3306/hospital`
- Connection timezone: UTC
- Messaging: application-defined Jakarta Messaging connection factory and queue; ensure the GlassFish Platform JMS service is enabled.
- Authentication: application-managed opaque bearer sessions. Accounts and PBKDF2-HMAC-SHA256 password hashes live in `hospital.user_accounts` (migration V012); the client calls `POST /api/v1/auth/login` and sends `Authorization: Bearer <token>` thereafter. GlassFish File Realm is not used by this edition.

The datasource script installs the Connector/J JAR into `glassfish/domains/domain1/lib`, restarts the domain, prompts for a GlassFish password alias, creates the XA pool and JNDI resource, and runs `ping-connection-pool`. It does not embed a database password in source code or command arguments.

## Runtime database account

Run `database/mysql/06_create_runtime_user.example.sql` manually as a MySQL administrator after schema installation. Replace its password placeholder. It grants only SELECT, INSERT, UPDATE and DELETE on `hospital.*` to a local TCP account at `127.0.0.1`. Do not use `root` for the running application. DDL privileges belong only to the administrator performing migrations.

## Build and runtime verification boundary

The source and SQL have been statically inspected for the target platform, JPA entity-to-table and mapped-column references, OpenAPI references, TypeScript syntax, XML well-formedness, and archive contents. The preparation environment does not contain the target Windows JDK 17.0.12, GlassFish 7.0.23, MySQL 8.0.38 or an installed Maven runtime, and dependency network access was unavailable. Therefore **Maven compilation, execution of MySQL DDL/triggers/seeds, GlassFish deployment, XA pool ping, JMS startup and browser end-to-end tests have not been performed**. They must be run on the target Windows installation before calling the package runtime-verified.

Use synthetic records only. The package is an academic software project, not a clinically certified product.
