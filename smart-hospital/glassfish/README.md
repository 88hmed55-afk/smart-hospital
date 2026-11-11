# GlassFish 7.0.23 / MySQL 8.0.38 setup (Windows)

1. Make sure you installed **GlassFish 7.0.23 Jakarta EE Platform 10**, not Web Profile. The application uses JMS and Message-Driven Beans.
2. Download MySQL Connector/J from the official MySQL site: https://dev.mysql.com/downloads/connector/j/ . Connector/J 9.7 supports Java 8+ and MySQL 8.0+, so it is suitable for the JDK 17 / MySQL 8.0.38 stack.
3. Put the JAR somewhere stable (for example `C:\mysql-connector\mysql-connector-j-9.7.0.jar`).
4. Create the least-privilege `smart_hospital_app` user in MySQL by editing and executing `database/mysql/06_create_runtime_user.example.sql` as a MySQL administrator. This user must not be `root`.
5. Update the GlassFish path and JAR path in `create-datasource.ps1`, then run it from PowerShell. It copies Connector/J into `glassfish\domains\domain1\lib`, restarts domain1, prompts for a GlassFish password alias, creates a MySQL XA connection pool, creates `jdbc/SmartHospitalDS`, and pings the pool.
6. Create the synthetic File Realm users with `create-demo-users.ps1`. The script prompts for a password for each demo user; choose local-only passwords and remember them for the login screen. These usernames and groups must exactly match the synthetic accounts/roles seeded in MySQL.
7. The application declares its JMS connection factory and queue using Jakarta EE resource-definition annotations. The GlassFish Platform distribution must have its JMS service enabled.

## Security notes

- Do not use MySQL root as the runtime datasource account.
- Do not commit database passwords or copy them into source files. The datasource script uses a GlassFish password alias rather than a password in the asadmin command line.
- The MySQL schema/trigger creation uses an administrator account and must be done separately from the runtime datasource.
- The development File Realm accounts are synthetic and are for localhost only. Use TLS and a reviewed identity provider for any real deployment.
- If a password alias already exists, remove it with `asadmin delete-password-alias SmartHospitalDbPassword` before rerunning the setup script. If the JDBC pool/resource already exists, remove the old resource and pool through the GlassFish administration console/asadmin before recreating them.
