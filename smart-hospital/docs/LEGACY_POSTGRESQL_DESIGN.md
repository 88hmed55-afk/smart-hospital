# Historical PostgreSQL design records — not used by this edition

The project originally started with PostgreSQL and Jakarta EE 11. Those files remain in the repository to preserve the design decisions and earlier release history. They are **not part of the current build or install path**.

For the user's installed toolchain, use only:
- JDK 17.0.12
- GlassFish 7.0.23 / Jakarta EE 10 Platform
- MySQL 8.0.38
- the SQL files in `database/mysql`
- the MySQL installer `database/apply-mysql-local.ps1`

The legacy scripts are now isolated under `database/legacy-postgresql/`; do not execute them for the current MySQL edition. Do not use any older PostgreSQL verification report as a status report for this MySQL package.
