-- Smart Hospital | One-time repair for MySQL RBAC data installed by V005.
-- The original V005 seed inserted LAB_SUPERVISOR without an explicit id, which
-- produced an empty primary key on CHAR(36) columns in non-strict MySQL modes.
-- This script is idempotent and safe to run on the existing hospital database.
USE hospital;

SET FOREIGN_KEY_CHECKS = 0;

SET @lab_role_id := UUID();
UPDATE roles
   SET id = @lab_role_id
 WHERE code = 'LAB_SUPERVISOR'
   AND (id IS NULL OR TRIM(id) = '');

UPDATE user_roles
   SET role_id = @lab_role_id
 WHERE role_id IS NULL OR TRIM(role_id) = '';

UPDATE role_permissions
   SET role_id = @lab_role_id
 WHERE role_id IS NULL OR TRIM(role_id) = '';

SET FOREIGN_KEY_CHECKS = 1;

SELECT id, code, name FROM roles WHERE code = 'LAB_SUPERVISOR';
