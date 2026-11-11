-- V016: Consent management and standard clinical coding starter catalogues.
USE hospital;

CREATE TABLE IF NOT EXISTS patient_consents (
    id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    patient_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consent_type       VARCHAR(60) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    granted_at         DATETIME(6) NULL,
    revoked_at         DATETIME(6) NULL,
    expires_at         DATETIME(6) NULL,
    document_reference VARCHAR(200) NULL,
    recorded_by        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    notes              VARCHAR(600) NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_patient_consent_status CHECK (status IN ('GRANTED','REVOKED','EXPIRED','PENDING')),
    CONSTRAINT fk_patient_consent_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_patient_consent_recorded_by FOREIGN KEY (recorded_by) REFERENCES user_accounts (id)
) ENGINE=InnoDB;

CREATE INDEX ix_patient_consents_patient ON patient_consents (patient_id, consent_type, status);

ALTER TABLE patients
    ADD COLUMN national_id VARCHAR(40) NULL AFTER mrn,
    ADD COLUMN national_id_type VARCHAR(30) NULL AFTER national_id;

CREATE UNIQUE INDEX uq_patients_national_id ON patients (national_id_type, national_id);
CREATE INDEX ix_patients_name_dob ON patients (last_name, first_name, date_of_birth);

ALTER TABLE lab_test_catalog
    ADD COLUMN loinc_code VARCHAR(20) NULL AFTER code,
    ADD COLUMN code_system VARCHAR(32) NOT NULL DEFAULT 'LOCAL' AFTER name;

CREATE UNIQUE INDEX uq_lab_test_loinc ON lab_test_catalog (loinc_code);

UPDATE lab_test_catalog SET loinc_code = '58410-2', code_system = 'LOINC' WHERE code = 'DEMO-LAB-CBC';
UPDATE lab_test_catalog SET loinc_code = '2345-7', code_system = 'LOINC' WHERE code = 'DEMO-LAB-GLU';

-- Starter LOINC set. Import the full LOINC release through the documented ETL job
-- before activating interoperability in a real hospital.
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-4548-4', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', 'LOINC', 'Blood', '%', '4.0-5.6', 0.00, 'YER', 120, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '4548-4');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-2160-0', '2160-0', 'Creatinine [Mass/volume] in Serum or Plasma', 'LOINC', 'Blood', 'mg/dL', '0.6-1.2', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '2160-0');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-2823-3', '2823-3', 'Potassium [Moles/volume] in Serum or Plasma', 'LOINC', 'Blood', 'mmol/L', '3.5-5.1', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '2823-3');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-2951-2', '2951-2', 'Sodium [Moles/volume] in Serum or Plasma', 'LOINC', 'Blood', 'mmol/L', '135-145', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '2951-2');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-718-7', '718-7', 'Hemoglobin [Mass/volume] in Blood', 'LOINC', 'Blood', 'g/dL', '12.0-17.5', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '718-7');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-6690-2', '6690-2', 'Leukocytes [#/volume] in Blood by Automated count', 'LOINC', 'Blood', '10^3/uL', '4.0-11.0', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '6690-2');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-777-3', '777-3', 'Platelets [#/volume] in Blood by Automated count', 'LOINC', 'Blood', '10^3/uL', '150-400', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '777-3');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-1742-6', '1742-6', 'Alanine aminotransferase [Enzymatic activity/volume] in Serum or Plasma', 'LOINC', 'Blood', 'U/L', '7-56', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '1742-6');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-1920-8', '1920-8', 'Aspartate aminotransferase [Enzymatic activity/volume] in Serum or Plasma', 'LOINC', 'Blood', 'U/L', '10-40', 0.00, 'YER', 60, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '1920-8');
INSERT INTO lab_test_catalog (id, code, loinc_code, name, code_system, specimen_type, result_unit, reference_range, standard_price, currency_code, expected_minutes, is_active)
SELECT UUID(), 'LOINC-3016-3', '3016-3', 'Thyrotropin [Units/volume] in Serum or Plasma', 'LOINC', 'Blood', 'mIU/L', '0.4-4.0', 0.00, 'YER', 120, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lab_test_catalog WHERE loinc_code = '3016-3');

-- Starter ICD-10 set. Import a licensed ICD-10-CM/ICD-10-WHO release before
-- using the catalogue for statutory morbidity reporting.
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'E11.9', 'Type 2 diabetes mellitus without complications', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'E11.9');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'I10', 'Essential (primary) hypertension', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'I10');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'J18.9', 'Pneumonia, unspecified organism', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'J18.9');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'N39.0', 'Urinary tract infection, site not specified', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'N39.0');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'K21.9', 'Gastro-esophageal reflux disease without esophagitis', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'K21.9');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'J45.909', 'Unspecified asthma, uncomplicated', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'J45.909');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'I48.91', 'Unspecified atrial fibrillation', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'I48.91');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'N18.9', 'Chronic kidney disease, unspecified', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'N18.9');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'A09', 'Infectious gastroenteritis and colitis, unspecified', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'A09');
INSERT INTO diagnosis_catalog (id, code_system, diagnosis_code, description, is_active)
SELECT UUID(), 'ICD-10', 'R50.9', 'Fever, unspecified', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM diagnosis_catalog WHERE code_system = 'ICD-10' AND diagnosis_code = 'R50.9');
