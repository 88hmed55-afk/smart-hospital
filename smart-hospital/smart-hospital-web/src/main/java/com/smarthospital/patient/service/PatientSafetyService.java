package com.smarthospital.patient.service;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.common.persistence.DbValues;
import com.smarthospital.patient.dto.AllergyCreateRequest;
import com.smarthospital.patient.dto.AllergyResponse;
import com.smarthospital.patient.dto.ConsentCreateRequest;
import com.smarthospital.patient.dto.ConsentResponse;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Maintains the patient safety record that clinical screening depends on: documented
 * allergies and consent decisions. Every mutation is written to the audit trail.
 */
@Stateless
public class PatientSafetyService {

    private static final Set<String> ALLERGY_SEVERITIES =
            Set.of("MILD", "MODERATE", "SEVERE", "LIFE_THREATENING", "UNKNOWN");
    private static final Set<String> ALLERGY_VERIFICATION =
            Set.of("UNVERIFIED", "VERIFIED", "CONFIRMED", "REFUTED", "ENTERED_IN_ERROR", "RULED_OUT");
    private static final Set<String> CONSENT_STATUSES =
            Set.of("GRANTED", "REVOKED", "EXPIRED", "PENDING");

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService currentUserService;

    @EJB
    private AuditService auditService;

    public List<AllergyResponse> listAllergies(UUID patientId) {
        requirePatient(patientId);
        currentUserService.requirePatientAccess(patientId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, patient_id, allergen_name, reaction, severity, verification_status, recorded_at
                  FROM hospital.patient_allergies
                 WHERE patient_id = ?1
                 ORDER BY recorded_at DESC
                """)
                .setParameter(1, patientId)
                .getResultList();
        return rows.stream().map(PatientSafetyService::toAllergy).toList();
    }

    public AllergyResponse recordAllergy(UUID patientId, AllergyCreateRequest request) {
        if (request == null || request.allergenName == null || request.allergenName.isBlank()) {
            throw BusinessException.badRequest("ALLERGEN_REQUIRED", "The allergen name is required.");
        }
        requirePatient(patientId);
        currentUserService.requirePatientAccess(patientId);

        String severity = normalize(request.severity, "UNKNOWN");
        if (!ALLERGY_SEVERITIES.contains(severity)) {
            throw BusinessException.badRequest("ALLERGY_SEVERITY_INVALID",
                    "Severity must be MILD, MODERATE, SEVERE, LIFE_THREATENING or UNKNOWN.");
        }
        String verification = normalize(request.verificationStatus, "UNVERIFIED");
        if (!ALLERGY_VERIFICATION.contains(verification)) {
            throw BusinessException.badRequest("ALLERGY_STATUS_INVALID",
                    "Verification status must be UNVERIFIED, VERIFIED, CONFIRMED, REFUTED, ENTERED_IN_ERROR or RULED_OUT.");
        }

        String allergen = request.allergenName.trim();
        Long duplicate = ((Number) em.createNativeQuery("""
                SELECT count(*)
                  FROM hospital.patient_allergies
                 WHERE patient_id = ?1 AND lower(allergen_name) = lower(?2)
                """)
                .setParameter(1, patientId)
                .setParameter(2, allergen)
                .getSingleResult()).longValue();
        if (duplicate > 0) {
            throw BusinessException.conflict("ALLERGY_ALREADY_RECORDED",
                    "This allergen is already recorded for the patient.");
        }

        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO hospital.patient_allergies
                    (id, patient_id, allergen_name, reaction, severity, verification_status, recorded_by, recorded_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, CURRENT_TIMESTAMP(6))
                """)
                .setParameter(1, id)
                .setParameter(2, patientId)
                .setParameter(3, allergen)
                .setParameter(4, clean(request.reaction))
                .setParameter(5, severity)
                .setParameter(6, verification)
                .setParameter(7, currentUserService.requireCurrentUser().staffId())
                .executeUpdate();

        auditService.recordAction("ALLERGY_RECORDED", "PATIENT_ALLERGY", id,
                "allergy recorded with severity " + severity + " and status " + verification);
        return findById(patientId, id);
    }

    public AllergyResponse refuteAllergy(UUID patientId, UUID allergyId, String reason) {
        requirePatient(patientId);
        currentUserService.requirePatientAccess(patientId);
        int updated = em.createNativeQuery("""
                UPDATE hospital.patient_allergies
                   SET verification_status = 'REFUTED'
                 WHERE id = ?1 AND patient_id = ?2
                """)
                .setParameter(1, allergyId)
                .setParameter(2, patientId)
                .executeUpdate();
        if (updated == 0) {
            throw BusinessException.notFound("Allergy");
        }
        auditService.recordAction("ALLERGY_REFUTED", "PATIENT_ALLERGY", allergyId,
                "allergy marked as refuted. Reason: " + (reason == null || reason.isBlank() ? "not stated" : reason));
        return findById(patientId, allergyId);
    }

    public List<ConsentResponse> listConsents(UUID patientId) {
        requirePatient(patientId);
        currentUserService.requirePatientAccess(patientId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, patient_id, consent_type, status, granted_at, revoked_at, expires_at,
                       document_reference, notes, created_at
                  FROM hospital.patient_consents
                 WHERE patient_id = ?1
                 ORDER BY created_at DESC
                """)
                .setParameter(1, patientId)
                .getResultList();
        return rows.stream().map(PatientSafetyService::toConsent).toList();
    }

    public ConsentResponse recordConsent(UUID patientId, ConsentCreateRequest request) {
        if (request == null || request.consentType == null || request.consentType.isBlank()) {
            throw BusinessException.badRequest("CONSENT_TYPE_REQUIRED", "The consent type is required.");
        }
        requirePatient(patientId);
        currentUserService.requirePatientAccess(patientId);

        String consentType = request.consentType.trim().toUpperCase(Locale.ROOT);
        String status = normalize(request.status, "GRANTED");
        if (!CONSENT_STATUSES.contains(status)) {
            throw BusinessException.badRequest("CONSENT_STATUS_INVALID",
                    "Consent status must be GRANTED, REVOKED, EXPIRED or PENDING.");
        }
        if ("GRANTED".equals(status)) {
            Long active = ((Number) em.createNativeQuery("""
                    SELECT count(*)
                      FROM hospital.patient_consents
                     WHERE patient_id = ?1
                       AND consent_type = ?2
                       AND status = 'GRANTED'
                       AND revoked_at IS NULL
                       AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP(6))
                    """)
                    .setParameter(1, patientId)
                    .setParameter(2, consentType)
                    .getSingleResult()).longValue();
            if (active > 0) {
                throw BusinessException.conflict("CONSENT_ALREADY_GRANTED",
                        "An active consent of this type already exists for the patient.");
            }
        }

        UUID id = UUID.randomUUID();
        UUID currentUserId = currentUserService.requireCurrentUser().userId();
        em.createNativeQuery("""
                INSERT INTO hospital.patient_consents
                    (id, patient_id, consent_type, status, granted_at, expires_at,
                     document_reference, recorded_by, notes, created_at, updated_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """)
                .setParameter(1, id)
                .setParameter(2, patientId)
                .setParameter(3, consentType)
                .setParameter(4, status)
                .setParameter(5, "GRANTED".equals(status) ? Timestamp.from(Instant.now()) : null)
                .setParameter(6, request.expiresAt == null ? null : Timestamp.from(request.expiresAt))
                .setParameter(7, clean(request.documentReference))
                .setParameter(8, currentUserId)
                .setParameter(9, clean(request.notes))
                .executeUpdate();

        auditService.recordAction("CONSENT_RECORDED", "PATIENT_CONSENT", id,
                "consent " + consentType + " recorded with status " + status);
        return findConsent(patientId, id);
    }

    public ConsentResponse revokeConsent(UUID patientId, UUID consentId, String reason) {
        requirePatient(patientId);
        currentUserService.requirePatientAccess(patientId);
        int updated = em.createNativeQuery("""
                UPDATE hospital.patient_consents
                   SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6),
                       notes = CASE WHEN ?3 IS NULL THEN notes ELSE ?3 END
                 WHERE id = ?1 AND patient_id = ?2 AND status = 'GRANTED'
                """)
                .setParameter(1, consentId)
                .setParameter(2, patientId)
                .setParameter(3, clean(reason))
                .executeUpdate();
        if (updated == 0) {
            throw BusinessException.conflict("CONSENT_NOT_ACTIVE",
                    "Only an active granted consent can be revoked.");
        }
        auditService.recordAction("CONSENT_REVOKED", "PATIENT_CONSENT", consentId,
                "consent revoked. Reason: " + (reason == null || reason.isBlank() ? "not stated" : reason));
        return findConsent(patientId, consentId);
    }

    private void requirePatient(UUID patientId) {
        if (patientId == null) {
            throw BusinessException.badRequest("PATIENT_ID_REQUIRED", "The patient identifier is required.");
        }
        Long count = ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM hospital.patients WHERE id = ?1")
                .setParameter(1, patientId)
                .getSingleResult()).longValue();
        if (count == 0) {
            throw BusinessException.notFound("Patient");
        }
    }

    private AllergyResponse findById(UUID patientId, UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, patient_id, allergen_name, reaction, severity, verification_status, recorded_at
                  FROM hospital.patient_allergies
                 WHERE id = ?1 AND patient_id = ?2
                """)
                .setParameter(1, id)
                .setParameter(2, patientId)
                .getResultList();
        if (rows.isEmpty()) {
            throw BusinessException.notFound("Allergy");
        }
        return toAllergy(rows.get(0));
    }

    private ConsentResponse findConsent(UUID patientId, UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, patient_id, consent_type, status, granted_at, revoked_at, expires_at,
                       document_reference, notes, created_at
                  FROM hospital.patient_consents
                 WHERE id = ?1 AND patient_id = ?2
                """)
                .setParameter(1, id)
                .setParameter(2, patientId)
                .getResultList();
        if (rows.isEmpty()) {
            throw BusinessException.notFound("Consent");
        }
        return toConsent(rows.get(0));
    }

    private static AllergyResponse toAllergy(Object[] row) {
        AllergyResponse response = new AllergyResponse();
        response.id = DbValues.uuid(row[0]);
        response.patientId = DbValues.uuid(row[1]);
        response.allergenName = (String) row[2];
        response.reaction = (String) row[3];
        response.severity = (String) row[4];
        response.verificationStatus = (String) row[5];
        response.recordedAt = instant(row[6]);
        return response;
    }

    private static ConsentResponse toConsent(Object[] row) {
        ConsentResponse response = new ConsentResponse();
        response.id = DbValues.uuid(row[0]);
        response.patientId = DbValues.uuid(row[1]);
        response.consentType = (String) row[2];
        response.status = (String) row[3];
        response.grantedAt = instant(row[4]);
        response.revokedAt = instant(row[5]);
        response.expiresAt = instant(row[6]);
        response.documentReference = (String) row[7];
        response.notes = (String) row[8];
        response.createdAt = instant(row[9]);
        return response;
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        return null;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
