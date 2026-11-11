-- V018: Align patient allergy and consent check constraints with clinical vocabulary.
--
-- V014 introduced the medication-safety screening service, which recognises
-- LIFE_THREATENING reactions and VERIFIED / REFUTED allergy statuses. The original
-- schema only allowed MILD/MODERATE/SEVERE/UNKNOWN and UNVERIFIED/CONFIRMED/
-- ENTERED_IN_ERROR, so recording a verified or refuted allergy failed at the
-- database layer. This migration widens the accepted value sets; it is additive
-- and does not rewrite existing rows.
USE hospital;

ALTER TABLE patient_allergies
    DROP CHECK ck_allergy_severity,
    DROP CHECK ck_allergy_verification;

ALTER TABLE patient_allergies
    ADD CONSTRAINT ck_allergy_severity
        CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE', 'LIFE_THREATENING', 'UNKNOWN')),
    ADD CONSTRAINT ck_allergy_verification
        CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'CONFIRMED',
                                       'REFUTED', 'ENTERED_IN_ERROR', 'RULED_OUT'));

ALTER TABLE patient_consents
    DROP CHECK ck_patient_consent_status;

ALTER TABLE patient_consents
    ADD CONSTRAINT ck_patient_consent_status
        CHECK (status IN ('GRANTED', 'REVOKED', 'EXPIRED', 'PENDING'));
