-- V015: Optimistic locking for mutable clinical and financial records.
USE hospital;

ALTER TABLE patients         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE encounters       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE appointments     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admissions       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE invoices         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE prescriptions    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clinical_notes   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_accounts    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE system_settings  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE medications      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE journal_entries  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
