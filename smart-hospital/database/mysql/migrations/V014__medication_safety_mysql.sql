-- V014: Medication safety metadata and interaction catalogue.
USE hospital;

ALTER TABLE medications
    ADD COLUMN drug_class VARCHAR(80) NULL AFTER dosage_form,
    ADD COLUMN max_daily_dose DECIMAL(14,3) NULL AFTER strength,
    ADD COLUMN dose_unit VARCHAR(20) NULL AFTER max_daily_dose;

UPDATE medications SET dose_unit = 'unit', drug_class = 'DEMO-CLASS' WHERE code LIKE 'DEMO-MED-%';

CREATE TABLE IF NOT EXISTS drug_interactions (
    id                        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    medication_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interacting_medication_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    severity                  VARCHAR(20) NOT NULL,
    description               VARCHAR(400) NOT NULL,
    is_active                 TINYINT(1) NOT NULL DEFAULT 1,
    created_at                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_drug_interaction_severity CHECK (severity IN ('MINOR','MODERATE','MAJOR','CONTRAINDICATED')),
    CONSTRAINT ck_drug_interaction_distinct CHECK (medication_id <> interacting_medication_id),
    CONSTRAINT fk_drug_interaction_medication FOREIGN KEY (medication_id) REFERENCES medications (id),
    CONSTRAINT fk_drug_interaction_other FOREIGN KEY (interacting_medication_id) REFERENCES medications (id)
) ENGINE=InnoDB;

CREATE UNIQUE INDEX uq_drug_interaction_pair ON drug_interactions (medication_id, interacting_medication_id);

-- Synthetic demonstration interaction. Production deployments import a licensed
-- interaction database (for example a national formulary or a commercial DDI feed).
INSERT INTO drug_interactions (id, medication_id, interacting_medication_id, severity, description)
SELECT UUID(), a.id, b.id, 'MAJOR', 'Synthetic demonstration interaction: co-administration requires pharmacist review.'
FROM medications a, medications b
WHERE a.code = 'DEMO-MED-001' AND b.code = 'DEMO-MED-002'
  AND NOT EXISTS (
      SELECT 1 FROM drug_interactions d WHERE d.medication_id = a.id AND d.interacting_medication_id = b.id
  );
