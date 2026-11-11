-- Run manually as a MySQL administrator AFTER the schema is created.
-- Replace the placeholder password before executing. Never commit a real password.
CREATE USER IF NOT EXISTS 'smart_hospital_app'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_A_LONG_UNIQUE_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON hospital.* TO 'smart_hospital_app'@'127.0.0.1';
FLUSH PRIVILEGES;
-- Do not grant DROP, ALTER, CREATE, TRIGGER, GRANT OPTION or access to other databases.
