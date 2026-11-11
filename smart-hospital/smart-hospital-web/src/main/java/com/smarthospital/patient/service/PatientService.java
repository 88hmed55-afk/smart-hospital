package com.smarthospital.patient.service;

import com.smarthospital.common.api.PageResponse;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.audit.service.AuditService;
import com.smarthospital.patient.dto.PatientRegistrationRequest;
import com.smarthospital.patient.dto.PatientResponse;
import com.smarthospital.patient.dto.PatientUpdateRequest;
import com.smarthospital.patient.entity.Patient;
import com.smarthospital.patient.entity.PatientAllergy;
import com.smarthospital.patient.entity.PatientContact;
import com.smarthospital.patient.entity.PatientBranchAccess;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Locale;
import java.util.UUID;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class PatientService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private AuditService auditService;

    @EJB
    private CurrentUserService currentUserService;
    public PatientResponse register(PatientRegistrationRequest request) {
        if (request == null) throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "A patient registration body is required.");
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        UUID registrationBranchId = request.branchId == null ? currentUser.defaultBranchId() : request.branchId;
        currentUserService.requireBranchAccess(registrationBranchId);
        String mrn = normalizeMrn(request.mrn);
        Long duplicateCount = em.createQuery("select count(p) from Patient p where p.mrn = :mrn", Long.class)
                .setParameter("mrn", mrn)
                .getSingleResult();
        if (duplicateCount > 0) {
            throw BusinessException.conflict("PATIENT_MRN_EXISTS", "A patient with this medical record number already exists.");
        }

        if (request.contacts != null && request.contacts.stream().filter(c -> c.primary).count() > 1) {
            throw BusinessException.badRequest("MULTIPLE_PRIMARY_CONTACTS", "Only one contact can be marked as primary.");
        }

        Patient patient = Patient.register(
                mrn,
                request.firstName.trim(),
                trimToNull(request.middleName),
                request.lastName.trim(),
                request.dateOfBirth,
                request.sex,
                normalizeBloodGroup(request.bloodGroup),
                trimToNull(request.primaryPhone),
                normalizeEmail(request.email),
                trimToNull(request.addressText),
                request.preferredLanguage == null ? "ar" : request.preferredLanguage.trim().toLowerCase(Locale.ROOT)
        );

        if (request.contacts != null) {
            request.contacts.forEach(input -> patient.addContact(new PatientContact(
                    input.contactType,
                    trimToNull(input.contactName),
                    trimToNull(input.relationship),
                    trimToNull(input.phoneNumber),
                    normalizeEmail(input.email),
                    input.primary
            )));
        }
        if (request.allergies != null) {
            request.allergies.forEach(input -> patient.addAllergy(new PatientAllergy(
                    input.allergenName.trim(),
                    trimToNull(input.reaction),
                    input.severity,
                    input.verificationStatus
            )));
        }

        em.persist(patient);
        HospitalBranch registrationBranch = em.find(HospitalBranch.class, registrationBranchId);
        if (registrationBranch == null || !registrationBranch.isActive()) {
            throw BusinessException.forbidden("DEFAULT_BRANCH_INACTIVE", "The user's default hospital branch is not active.");
        }
        em.persist(new PatientBranchAccess(patient, registrationBranch, currentUser.userId(), "INITIAL_REGISTRATION"));
        try {
            em.flush();
        } catch (jakarta.persistence.PersistenceException exception) {
            if (hasSqlState(exception, "23505")) {
                throw BusinessException.conflict("PATIENT_MRN_EXISTS", "A patient with this medical record number already exists.");
            }
            throw exception;
        }
        auditService.recordAction("PATIENT_REGISTERED", "PATIENT", patient.getId(), "patient registration", registrationBranchId);
        return PatientResponse.from(patient);
    }
    public PatientResponse findById(UUID id) {
        currentUserService.requirePatientAccess(id);
        Patient patient = em.find(Patient.class, id);
        if (patient == null) throw BusinessException.notFound("Patient");
        auditService.recordAction("PATIENT_RECORD_VIEWED", "PATIENT", patient.getId(), "patient detail view");
        return PatientResponse.from(patient);
    }
    public PatientResponse updateDemographics(UUID id, PatientUpdateRequest request) {
        if (request == null) throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "A patient update body is required.");
        currentUserService.requirePatientAccess(id);
        Patient patient = em.find(Patient.class, id);
        if (patient == null) throw BusinessException.notFound("Patient");
        patient.updateDemographics(
                request.firstName.trim(),
                trimToNull(request.middleName),
                request.lastName.trim(),
                request.dateOfBirth,
                request.sex,
                normalizeBloodGroup(request.bloodGroup),
                trimToNull(request.primaryPhone),
                normalizeEmail(request.email),
                trimToNull(request.addressText),
                request.preferredLanguage == null ? "ar" : request.preferredLanguage.trim().toLowerCase(Locale.ROOT)
        );
        em.flush();
        auditService.recordAction("PATIENT_DEMOGRAPHICS_UPDATED", "PATIENT", patient.getId(), "demographic update");
        return PatientResponse.from(patient);
    }
    public PageResponse<com.smarthospital.patient.dto.PatientSummaryResponse> search(String query, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String normalized = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String pattern = normalized == null ? null : "%" + normalized.replace("%", "").replace("_", "") + "%";

        CurrentUser currentUser = currentUserService.requireCurrentUser();
        String where = "(exists (select pba.id from PatientBranchAccess pba "
                + "where pba.patient = p and pba.branch.id in :branchIds and pba.revokedAt is null)) "
                + "and (:pattern is null or lower(p.mrn) like :pattern "
                + "or lower(p.firstName) like :pattern "
                + "or lower(p.lastName) like :pattern "
                + "or lower(coalesce(p.middleName, '')) like :pattern)";

        var dataQuery = em.createQuery("select p from Patient p where " + where + " order by p.createdAt desc", Patient.class)
                .setParameter("branchIds", currentUser.branchIds())
                .setParameter("pattern", pattern)
                .setFirstResult(safeOffset)
                .setMaxResults(safeLimit);
        var countQuery = em.createQuery("select count(p) from Patient p where " + where, Long.class)
                .setParameter("branchIds", currentUser.branchIds())
                .setParameter("pattern", pattern);
        long total = countQuery.getSingleResult();
        auditService.recordAction("PATIENT_SEARCH_EXECUTED", "PATIENT", null,
                "search query supplied=" + (normalized != null) + "; offset=" + safeOffset + "; limit=" + safeLimit + "; total=" + total,
                currentUser.defaultBranchId());
        return new PageResponse<>(dataQuery.getResultList().stream().map(com.smarthospital.patient.dto.PatientSummaryResponse::from).toList(),
                total, safeOffset, safeLimit);
    }

    private boolean hasSqlState(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql && expected.equals(sql.getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }

    private String normalizeMrn(String value) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest("MRN_REQUIRED", "Medical record number is required.");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBloodGroup(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
