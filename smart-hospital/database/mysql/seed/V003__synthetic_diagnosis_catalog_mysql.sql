-- DEMONSTRATION DATA ONLY. These are deliberately synthetic labels/codes,
-- not a clinical terminology and not suitable for clinical use.

INSERT IGNORE INTO hospital.diagnosis_catalog (code_system, diagnosis_code, description, is_active)
VALUES
    ('DEMO', 'TEST-DIAG-001', 'Synthetic demonstration diagnosis alpha', TRUE),
    ('DEMO', 'TEST-DIAG-002', 'Synthetic demonstration diagnosis beta', TRUE),
    ('DEMO', 'TEST-DIAG-003', 'Synthetic demonstration diagnosis gamma', TRUE);
