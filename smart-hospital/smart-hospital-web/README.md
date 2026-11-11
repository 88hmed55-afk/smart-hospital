# Smart Hospital Web Module (WAR)

- Java target: 17
- Platform: Jakarta EE 10.0.0
- Server: GlassFish 7.0.23 (Jakarta EE Platform 10)
- Package: WAR (`smart-hospital-web.war`), bundled inside the deployable EAR
- Database: MySQL 8.0.38 using the GlassFish JTA XA datasource `jdbc/SmartHospitalDS`
- ORM: Jakarta Persistence 3.1, with the EclipseLink version supplied by GlassFish 7
- Messaging: Jakarta Messaging 3.1 / MDB via GlassFish JMS

This module is built as part of the parent Maven reactor. Open the parent folder `smart-hospital` (which contains the aggregator `pom.xml`) in NetBeans 21 and run Clean and Build. The generated WAR is `smart-hospital-web/target/smart-hospital-web.war`, and the deployable archive is `smart-hospital-ear/target/smart-hospital.ear` (context root `/`).

The application expects MySQL Connector/J to be installed into the GlassFish domain library. Configure the `jdbc/SmartHospitalDS` XA pool before deployment. Do not add another Jakarta EE API or EclipseLink implementation JAR to the WAR; GlassFish supplies those platform APIs and its JPA provider.

PostgreSQL is not used by this edition. The old PostgreSQL SQL files are retained outside the MySQL installer only for design history.
