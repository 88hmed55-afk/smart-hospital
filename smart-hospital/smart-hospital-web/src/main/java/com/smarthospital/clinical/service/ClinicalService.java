package com.smarthospital.clinical.service;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.clinical.dto.CareTeamAssignRequest;
import com.smarthospital.clinical.dto.CareTeamResponse;
import com.smarthospital.clinical.dto.ClinicalNoteCreateRequest;
import com.smarthospital.clinical.dto.ClinicalNoteResponse;
import com.smarthospital.clinical.dto.DiagnosisCatalogResponse;
import com.smarthospital.clinical.dto.DiagnosisCreateRequest;
import com.smarthospital.clinical.dto.DiagnosisStatusUpdateRequest;
import com.smarthospital.clinical.dto.EncounterCreateRequest;
import com.smarthospital.clinical.dto.EncounterDiagnosisResponse;
import com.smarthospital.clinical.dto.EncounterResponse;
import com.smarthospital.clinical.dto.VitalSignRequest;
import com.smarthospital.clinical.dto.VitalSignResponse;
import com.smarthospital.clinical.entity.CareRole;
import com.smarthospital.clinical.entity.ClinicalNote;
import com.smarthospital.clinical.entity.ClinicalNoteStatus;
import com.smarthospital.clinical.entity.ClinicalNoteType;
import com.smarthospital.clinical.entity.DiagnosisCatalog;
import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.clinical.entity.EncounterCareTeam;
import com.smarthospital.clinical.entity.EncounterDiagnosis;
import com.smarthospital.clinical.entity.EncounterStatus;
import com.smarthospital.clinical.entity.VitalSign;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.entity.Department;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.organization.entity.StaffMember;
import com.smarthospital.patient.entity.Patient;
import com.smarthospital.patient.entity.PatientStatus;
import com.smarthospital.scheduling.entity.Appointment;
import com.smarthospital.scheduling.entity.AppointmentStatus;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Application service for the physician-led clinical encounter workflow.
 * Mutating operations lock the encounter row so completion cannot race with
 * note, diagnosis or observation updates.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class ClinicalService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService currentUserService;

    @EJB
    private AuditService auditService;
    public EncounterResponse openEncounter(EncounterCreateRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "An encounter request body is required.");
        }

        CurrentUser user = currentUserService.requireCurrentUser();
        UUID doctorStaffId = currentUserService.requireDoctorStaffId();
        currentUserService.requireBranchAccess(request.branchId);
        currentUserService.requirePatientAccessInBranch(request.patientId, request.branchId);

        HospitalBranch branch = em.find(HospitalBranch.class, request.branchId);
        Patient patient = em.find(Patient.class, request.patientId);
        Department department = em.find(Department.class, request.departmentId);
        DoctorProfile doctor = em.find(DoctorProfile.class, doctorStaffId);

        if (branch == null || !branch.isActive()) {
            throw BusinessException.notFound("Active hospital branch");
        }
        if (patient == null) {
            throw BusinessException.notFound("Patient");
        }
        if (patient.getPatientStatus() != PatientStatus.ACTIVE) {
            throw BusinessException.conflict(
                    "PATIENT_NOT_ACTIVE",
                    "A new encounter cannot be opened for an inactive, deceased or merged patient record.");
        }
        if (department == null || !department.isActive()) {
            throw BusinessException.notFound("Active department");
        }
        if (doctor == null || !doctor.isActive() || !doctor.getStaff().isActive()) {
            throw BusinessException.forbidden(
                    "ACTIVE_DOCTOR_PROFILE_REQUIRED",
                    "An active doctor profile is required.");
        }

        boolean branchMismatch = !branch.getId().equals(department.getBranch().getId())
                || !branch.getId().equals(doctor.getStaff().getBranch().getId());
        if (branchMismatch) {
            throw BusinessException.badRequest(
                    "ENCOUNTER_BRANCH_MISMATCH",
                    "The doctor, department and encounter must belong to the same branch.");
        }

        Department doctorDepartment = doctor.getStaff().getDepartment();
        if (doctorDepartment == null || !doctorDepartment.getId().equals(department.getId())) {
            throw BusinessException.badRequest(
                    "DOCTOR_DEPARTMENT_MISMATCH",
                    "The doctor is not assigned to the selected department.");
        }

        Appointment appointment = null;
        if (request.appointmentId != null) {
            appointment = em.find(
                    Appointment.class,
                    request.appointmentId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (appointment == null) {
                throw BusinessException.notFound("Appointment");
            }

            boolean appointmentMismatch = !appointment.getBranchId().equals(request.branchId)
                    || !appointment.getPatientId().equals(request.patientId)
                    || !appointment.getDoctorStaffId().equals(doctorStaffId)
                    || !appointment.getDepartmentId().equals(request.departmentId);
            if (appointmentMismatch) {
                throw BusinessException.badRequest(
                        "APPOINTMENT_ENCOUNTER_MISMATCH",
                        "The appointment does not match this patient, doctor, department and branch.");
            }
            if (appointment.getStatus() != AppointmentStatus.CHECKED_IN) {
                throw BusinessException.conflict(
                        "APPOINTMENT_NOT_CHECKED_IN",
                        "The appointment must be checked in before an encounter can be opened.");
            }
        }

        Encounter encounter = Encounter.open(
                request.branchId,
                patient,
                appointment,
                department,
                doctor,
                request.encounterType,
                clean(request.chiefComplaint),
                user.userId());
        em.persist(encounter);
        em.flush();

        auditService.recordAction(
                "ENCOUNTER_OPENED",
                "ENCOUNTER",
                encounter.getId(),
                "clinical encounter opened",
                request.branchId);
        return EncounterResponse.from(encounter);
    }
    public List<EncounterResponse> listPatientEncounters(UUID patientId, int limit) {
        currentUserService.requirePatientAccess(patientId);
        CurrentUser user = currentUserService.requireCurrentUser();
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        StringBuilder jpql = new StringBuilder(
                "select e from Encounter e where e.patient.id = :patientId "
                        + "and e.branchId in :branchIds "
                        + "and exists (select pba.id from PatientBranchAccess pba "
                        + "where pba.patient = e.patient and pba.branch.id = e.branchId "
                        + "and pba.revokedAt is null) ");

        if (user.roleCodes().contains("DOCTOR")) {
            jpql.append("and e.attendingDoctor.staffId = :staffId ");
        } else {
            jpql.append("and exists (select ct.id from EncounterCareTeam ct "
                    + "where ct.encounter = e and ct.staff.id = :staffId and ct.releasedAt is null) ");
        }
        jpql.append("order by e.startedAt desc");

        var query = em.createQuery(jpql.toString(), Encounter.class)
                .setParameter("patientId", patientId)
                .setParameter("branchIds", user.branchIds())
                .setMaxResults(safeLimit);
        query.setParameter("staffId", currentUserService.requireStaffId());
        return query.getResultList().stream().map(EncounterResponse::from).toList();
    }
    public EncounterResponse findEncounter(UUID encounterId) {
        Encounter encounter = requireEncounter(encounterId);
        auditService.recordAction(
                "ENCOUNTER_VIEWED",
                "ENCOUNTER",
                encounter.getId(),
                "encounter detail viewed",
                encounter.getBranchId());
        return EncounterResponse.from(encounter);
    }
    public VitalSignResponse recordVitals(UUID encounterId, VitalSignRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "A vital-sign request body is required.");
        }

        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);

        CurrentUser user = currentUserService.requireCurrentUser();
        UUID staffId = currentUserService.requireStaffId();
        StaffMember staff = requireActiveStaffInBranch(staffId, encounter.getBranchId());
        if (user.roleCodes().contains("DOCTOR")) {
            requireAttendingDoctor(encounter, staffId);
        } else {
            requireNurseCareTeamAccess(encounter, staffId);
        }

        if (!hasAnyMeasurement(request)) {
            throw BusinessException.badRequest(
                    "VITAL_MEASUREMENT_REQUIRED",
                    "At least one vital-sign measurement is required.");
        }
        if ((request.systolicMmhg == null) != (request.diastolicMmhg == null)) {
            throw BusinessException.badRequest(
                    "BLOOD_PRESSURE_PAIR_REQUIRED",
                    "Systolic and diastolic pressure must be supplied together.");
        }
        if (request.systolicMmhg != null && request.systolicMmhg <= request.diastolicMmhg) {
            throw BusinessException.badRequest(
                    "BLOOD_PRESSURE_INVALID",
                    "Systolic pressure must be greater than diastolic pressure.");
        }

        VitalSign vitalSign = VitalSign.record(
                encounter,
                staff,
                request.temperatureC,
                request.systolicMmhg,
                request.diastolicMmhg,
                request.pulseBpm,
                request.respiratoryRate,
                request.oxygenSaturation,
                request.weightKg,
                request.heightCm,
                clean(request.notes));
        em.persist(vitalSign);
        em.flush();

        auditService.recordAction(
                "VITAL_SIGNS_RECORDED",
                "ENCOUNTER",
                encounter.getId(),
                "vital signs recorded",
                encounter.getBranchId());
        return VitalSignResponse.from(vitalSign);
    }
    public List<VitalSignResponse> listVitals(UUID encounterId) {
        Encounter encounter = requireEncounter(encounterId);
        return em.createQuery(
                        "select v from VitalSign v "
                                + "where v.encounter.id = :encounterId "
                                + "order by v.measuredAt desc",
                        VitalSign.class)
                .setParameter("encounterId", encounter.getId())
                .setMaxResults(200)
                .getResultList()
                .stream()
                .map(VitalSignResponse::from)
                .toList();
    }
    public ClinicalNoteResponse createNote(UUID encounterId, ClinicalNoteCreateRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "A clinical note request body is required.");
        }

        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);
        CurrentUser user = currentUserService.requireCurrentUser();
        UUID staffId = currentUserService.requireStaffId();
        StaffMember author = requireActiveStaffInBranch(staffId, encounter.getBranchId());

        if (user.roleCodes().contains("DOCTOR")) {
            requireAttendingDoctor(encounter, staffId);
        } else {
            requireNurseCareTeamAccess(encounter, staffId);
        }
        if (user.roleCodes().contains("NURSE") && request.noteType != ClinicalNoteType.PROGRESS) {
            throw BusinessException.forbidden(
                    "NURSE_NOTE_TYPE_RESTRICTED",
                    "Nursing users may create progress notes only in this workflow.");
        }

        ClinicalNote note = ClinicalNote.draft(
                encounter,
                author,
                request.noteType,
                request.noteText.trim(),
                null);
        em.persist(note);
        em.flush();

        auditService.recordAction(
                "CLINICAL_NOTE_CREATED",
                "CLINICAL_NOTE",
                note.getId(),
                "draft clinical note created",
                encounter.getBranchId());
        return ClinicalNoteResponse.from(note);
    }
    public ClinicalNoteResponse createAmendment(
            UUID encounterId,
            UUID noteId,
            ClinicalNoteCreateRequest request) {
        if (request == null || request.noteText == null || request.noteText.isBlank()) {
            throw BusinessException.badRequest("NOTE_TEXT_REQUIRED", "Amendment text is required.");
        }

        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);
        UUID staffId = currentUserService.requireDoctorStaffId();
        requireAttendingDoctor(encounter, staffId);

        ClinicalNote original = em.find(ClinicalNote.class, noteId);
        if (original == null || !original.getEncounterId().equals(encounterId)) {
            throw BusinessException.notFound("Clinical note");
        }
        if (original.getNoteStatus() == ClinicalNoteStatus.DRAFT) {
            throw BusinessException.conflict(
                    "DRAFT_NOTE_NOT_AMENDABLE",
                    "Sign the original note before creating an amendment.");
        }
        if (request.noteType != original.getNoteType()) {
            throw BusinessException.badRequest(
                    "AMENDMENT_TYPE_MISMATCH",
                    "An amendment must retain the original note type.");
        }

        StaffMember author = requireActiveStaffInBranch(staffId, encounter.getBranchId());
        ClinicalNote amendment = ClinicalNote.draft(
                encounter,
                author,
                original.getNoteType(),
                request.noteText.trim(),
                original);
        em.persist(amendment);
        em.flush();

        auditService.recordAction(
                "CLINICAL_NOTE_AMENDMENT_CREATED",
                "CLINICAL_NOTE",
                amendment.getId(),
                "draft amendment created",
                encounter.getBranchId());
        return ClinicalNoteResponse.from(amendment);
    }
    public ClinicalNoteResponse signNote(UUID encounterId, UUID noteId) {
        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);

        CurrentUser user = currentUserService.requireCurrentUser();
        UUID staffId = currentUserService.requireStaffId();
        ClinicalNote note = em.find(ClinicalNote.class, noteId);
        if (note == null || !note.getEncounterId().equals(encounterId)) {
            throw BusinessException.notFound("Clinical note");
        }
        if (!note.getAuthorStaffId().equals(staffId)) {
            throw BusinessException.forbidden(
                    "NOTE_AUTHOR_REQUIRED",
                    "Only the note author may sign this draft.");
        }

        if (user.roleCodes().contains("DOCTOR")) {
            requireAttendingDoctor(encounter, staffId);
        } else {
            requireNurseCareTeamAccess(encounter, staffId);
        }
        if (user.roleCodes().contains("NURSE")
                && (note.getNoteType() != ClinicalNoteType.PROGRESS || note.getAmendedFrom() != null)) {
            throw BusinessException.forbidden(
                    "NURSE_NOTE_SIGN_RESTRICTED",
                    "Nursing users may sign their own progress notes only.");
        }

        try {
            note.sign();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("NOTE_ALREADY_SIGNED", "Only a draft note can be signed.");
        }
        em.flush();

        auditService.recordAction(
                "CLINICAL_NOTE_SIGNED",
                "CLINICAL_NOTE",
                note.getId(),
                "clinical note signed",
                encounter.getBranchId());
        return ClinicalNoteResponse.from(note);
    }
    public List<ClinicalNoteResponse> listNotes(UUID encounterId) {
        Encounter encounter = requireEncounter(encounterId);
        return em.createQuery(
                        "select n from ClinicalNote n "
                                + "where n.encounter.id = :encounterId "
                                + "order by n.createdAt desc",
                        ClinicalNote.class)
                .setParameter("encounterId", encounter.getId())
                .setMaxResults(200)
                .getResultList()
                .stream()
                .map(ClinicalNoteResponse::from)
                .toList();
    }
    public EncounterDiagnosisResponse addDiagnosis(UUID encounterId, DiagnosisCreateRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "A diagnosis request body is required.");
        }

        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);
        UUID staffId = currentUserService.requireDoctorStaffId();
        requireAttendingDoctor(encounter, staffId);

        DiagnosisCatalog diagnosis = em.find(DiagnosisCatalog.class, request.diagnosisId);
        if (diagnosis == null || !diagnosis.isActive()) {
            throw BusinessException.notFound("Active diagnosis catalog item");
        }

        if (request.primary) {
            Long existingPrimaryCount = em.createQuery(
                            "select count(d) from EncounterDiagnosis d "
                                    + "where d.encounter.id = :encounterId "
                                    + "and d.primaryDiagnosis = true",
                            Long.class)
                    .setParameter("encounterId", encounterId)
                    .getSingleResult();
            if (existingPrimaryCount > 0) {
                throw BusinessException.conflict(
                        "PRIMARY_DIAGNOSIS_EXISTS",
                        "This encounter already has a primary diagnosis.");
            }
        }

        StaffMember doctor = requireActiveStaffInBranch(staffId, encounter.getBranchId());
        EncounterDiagnosis link = EncounterDiagnosis.record(
                encounter,
                diagnosis,
                doctor,
                request.primary,
                request.clinicalStatus);
        em.persist(link);

        try {
            em.flush();
        } catch (PersistenceException exception) {
            if (hasSqlState(exception, "23505")) {
                throw BusinessException.conflict(
                        "ENCOUNTER_DIAGNOSIS_EXISTS",
                        "This diagnosis is already recorded for the encounter.");
            }
            throw exception;
        }

        auditService.recordAction(
                "ENCOUNTER_DIAGNOSIS_RECORDED",
                "ENCOUNTER_DIAGNOSIS",
                link.getId(),
                "diagnosis recorded",
                encounter.getBranchId());
        return EncounterDiagnosisResponse.from(link);
    }
    public List<EncounterDiagnosisResponse> listDiagnoses(UUID encounterId) {
        Encounter encounter = requireEncounter(encounterId);
        return em.createQuery(
                        "select d from EncounterDiagnosis d join fetch d.diagnosis "
                                + "where d.encounter.id = :encounterId "
                                + "order by d.primaryDiagnosis desc, d.recordedAt",
                        EncounterDiagnosis.class)
                .setParameter("encounterId", encounter.getId())
                .setMaxResults(100)
                .getResultList()
                .stream()
                .map(EncounterDiagnosisResponse::from)
                .toList();
    }
    public EncounterDiagnosisResponse updateDiagnosisStatus(
            UUID encounterId,
            UUID encounterDiagnosisId,
            DiagnosisStatusUpdateRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "A diagnosis status request body is required.");
        }

        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);
        UUID staffId = currentUserService.requireDoctorStaffId();
        requireAttendingDoctor(encounter, staffId);

        EncounterDiagnosis link = em.find(EncounterDiagnosis.class, encounterDiagnosisId);
        if (link == null || !link.getEncounter().getId().equals(encounterId)) {
            throw BusinessException.notFound("Encounter diagnosis");
        }
        link.updateClinicalStatus(request.clinicalStatus);
        em.flush();

        auditService.recordAction(
                "ENCOUNTER_DIAGNOSIS_STATUS_UPDATED",
                "ENCOUNTER_DIAGNOSIS",
                link.getId(),
                "diagnosis status updated",
                encounter.getBranchId());
        return EncounterDiagnosisResponse.from(link);
    }
    public EncounterResponse completeEncounter(UUID encounterId) {
        Encounter encounter = requireEncounterForUpdate(encounterId);
        UUID staffId = currentUserService.requireDoctorStaffId();
        requireAttendingDoctor(encounter, staffId);
        requireActiveEncounter(encounter);

        Long draftCount = em.createQuery(
                        "select count(n) from ClinicalNote n "
                                + "where n.encounter.id = :encounterId "
                                + "and n.noteStatus = :status",
                        Long.class)
                .setParameter("encounterId", encounterId)
                .setParameter("status", ClinicalNoteStatus.DRAFT)
                .getSingleResult();
        if (draftCount > 0) {
            throw BusinessException.conflict(
                    "UNSIGNED_NOTES_REMAIN",
                    "All clinical notes must be signed before completing the encounter.");
        }

        encounter.complete();
        if (encounter.getAppointment() != null) {
            em.lock(encounter.getAppointment(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            try {
                encounter.getAppointment().completeAfterEncounter();
            } catch (IllegalStateException exception) {
                throw BusinessException.conflict(
                        "APPOINTMENT_STATE_CONFLICT",
                        "The linked appointment is not in the checked-in state.");
            }
        }

        em.flush();
        auditService.recordAction(
                "ENCOUNTER_COMPLETED",
                "ENCOUNTER",
                encounter.getId(),
                "encounter completed",
                encounter.getBranchId());
        return EncounterResponse.from(encounter);
    }
    public CareTeamResponse assignCareTeamMember(UUID encounterId, CareTeamAssignRequest request) {
        if (request == null || request.staffId == null || request.careRole == null) {
            throw BusinessException.badRequest("CARE_TEAM_REQUEST_INVALID", "A staff member and care role are required.");
        }
        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);
        CurrentUser actor = currentUserService.requireCurrentUser();
        if (actor.roleCodes().contains("DOCTOR")) {
            requireAttendingDoctor(encounter, currentUserService.requireDoctorStaffId());
        }
        StaffMember nurse = requireActiveStaffInBranch(request.staffId, encounter.getBranchId());
        Long nurseRoleCount = ((Number) em.createNativeQuery("""
                SELECT count(*)
                  FROM hospital.user_accounts ua
                  JOIN hospital.user_roles ur ON ur.user_id=ua.id
                  JOIN hospital.roles r ON r.id=ur.role_id
                  JOIN hospital.user_branch_access uba ON uba.user_id=ua.id AND uba.branch_id=?1
                 WHERE ua.staff_id=?2 AND ua.account_status='ACTIVE' AND r.code='NURSE'
                """)
                .setParameter(2, request.staffId)
                .setParameter(1, encounter.getBranchId())
                .getSingleResult()).longValue();
        if (nurseRoleCount == 0) {
            throw BusinessException.badRequest("CARE_TEAM_MEMBER_NOT_NURSE", "Only an active nurse account may be assigned to the nursing care team.");
        }

        EncounterCareTeam assignment = EncounterCareTeam.assign(
                encounter, nurse, request.careRole, actor.userId());
        em.persist(assignment);
        try {
            em.flush();
        } catch (PersistenceException exception) {
            if (hasSqlState(exception, "23505")) {
                throw BusinessException.conflict("CARE_TEAM_ASSIGNMENT_EXISTS", "The nurse already has an active assignment or the primary nurse slot is occupied.");
            }
            throw exception;
        }
        auditService.recordAction("CARE_TEAM_MEMBER_ASSIGNED", "ENCOUNTER_CARE_TEAM", assignment.getId(), "nursing staff assigned", encounter.getBranchId());
        return CareTeamResponse.from(assignment);
    }
    public List<CareTeamResponse> listCareTeam(UUID encounterId) {
        Encounter encounter = requireEncounter(encounterId);
        return em.createQuery(
                        "select ct from EncounterCareTeam ct join fetch ct.staff "
                                + "where ct.encounter.id=:encounterId order by ct.assignedAt",
                        EncounterCareTeam.class)
                .setParameter("encounterId", encounter.getId())
                .setMaxResults(50)
                .getResultList().stream().map(CareTeamResponse::from).toList();
    }
    public CareTeamResponse releaseCareTeamMember(UUID encounterId, UUID assignmentId) {
        Encounter encounter = requireEncounterForUpdate(encounterId);
        requireActiveEncounter(encounter);
        CurrentUser actor = currentUserService.requireCurrentUser();
        if (actor.roleCodes().contains("DOCTOR")) {
            requireAttendingDoctor(encounter, currentUserService.requireDoctorStaffId());
        }
        EncounterCareTeam assignment = em.find(
                EncounterCareTeam.class, assignmentId,
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (assignment == null || !assignment.getEncounterId().equals(encounterId)) {
            throw BusinessException.notFound("Encounter care team assignment");
        }
        try {
            assignment.release();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("CARE_TEAM_ASSIGNMENT_RELEASED", "The assignment is already released.");
        }
        em.flush();
        auditService.recordAction("CARE_TEAM_MEMBER_RELEASED", "ENCOUNTER_CARE_TEAM", assignment.getId(), "nursing staff released", encounter.getBranchId());
        return CareTeamResponse.from(assignment);
    }
    public List<DiagnosisCatalogResponse> searchDiagnosisCatalog(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw BusinessException.badRequest(
                    "DIAGNOSIS_QUERY_INVALID",
                    "Enter between 2 and 80 characters to search the diagnosis catalog.");
        }

        String pattern = "%" + normalized.replace("%", "").replace("_", "") + "%";
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<DiagnosisCatalog> matches = em.createQuery(
                        "select d from DiagnosisCatalog d "
                                + "where d.active = true "
                                + "and (lower(d.diagnosisCode) like :pattern "
                                + "or lower(d.description) like :pattern) "
                                + "order by d.diagnosisCode",
                        DiagnosisCatalog.class)
                .setParameter("pattern", pattern)
                .setMaxResults(safeLimit)
                .getResultList();

        return matches.stream().map(this::toDiagnosisCatalogResponse).toList();
    }

    private DiagnosisCatalogResponse toDiagnosisCatalogResponse(DiagnosisCatalog diagnosis) {
        DiagnosisCatalogResponse response = new DiagnosisCatalogResponse();
        response.id = diagnosis.getId();
        response.codeSystem = diagnosis.getCodeSystem();
        response.diagnosisCode = diagnosis.getDiagnosisCode();
        response.description = diagnosis.getDescription();
        response.active = diagnosis.isActive();
        return response;
    }

    private Encounter requireEncounter(UUID encounterId) {
        Encounter encounter = em.find(Encounter.class, encounterId);
        if (encounter == null) {
            throw BusinessException.notFound("Encounter");
        }
        checkEncounterAccess(encounter);
        return encounter;
    }

    private Encounter requireEncounterForUpdate(UUID encounterId) {
        Encounter encounter = em.find(
                Encounter.class,
                encounterId,
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (encounter == null) {
            throw BusinessException.notFound("Encounter");
        }
        checkEncounterAccess(encounter);
        return encounter;
    }

    private void checkEncounterAccess(Encounter encounter) {
        currentUserService.requireBranchAccess(encounter.getBranchId());
        currentUserService.requirePatientAccessInBranch(encounter.getPatientId(), encounter.getBranchId());
        CurrentUser user = currentUserService.requireCurrentUser();
        if (user.roleCodes().contains("DOCTOR")) {
            requireAttendingDoctor(encounter, currentUserService.requireDoctorStaffId());
        } else if (user.roleCodes().contains("NURSE")) {
            requireNurseCareTeamAccess(encounter, currentUserService.requireStaffId());
        }
    }

    private void requireNurseCareTeamAccess(Encounter encounter, UUID staffId) {
        Long activeAssignment = ((Number) em.createNativeQuery("""
                SELECT count(*)
                  FROM hospital.encounter_care_team ct
                 WHERE ct.encounter_id = ?1
                   AND ct.staff_id = ?2
                   AND ct.released_at IS NULL
                """)
                .setParameter(1, encounter.getId())
                .setParameter(2, staffId)
                .getSingleResult()).longValue();
        if (activeAssignment == 0) {
            throw BusinessException.forbidden(
                    "ENCOUNTER_CARE_TEAM_REQUIRED",
                    "The nurse must be assigned to this encounter's care team.");
        }
    }

    private void requireActiveEncounter(Encounter encounter) {
        if (encounter.getStatus() != EncounterStatus.OPEN
                && encounter.getStatus() != EncounterStatus.IN_PROGRESS) {
            throw BusinessException.conflict(
                    "ENCOUNTER_NOT_ACTIVE",
                    "The encounter is no longer open for clinical changes.");
        }
    }

    private StaffMember requireActiveStaffInBranch(UUID staffId, UUID branchId) {
        StaffMember staff = em.find(StaffMember.class, staffId);
        if (staff == null
                || !staff.isActive()
                || !staff.getBranch().getId().equals(branchId)) {
            throw BusinessException.forbidden(
                    "STAFF_BRANCH_SCOPE_DENIED",
                    "The staff member is not active in this encounter branch.");
        }
        return staff;
    }

    private void requireAttendingDoctor(Encounter encounter, UUID staffId) {
        if (!encounter.getAttendingDoctorId().equals(staffId)) {
            throw BusinessException.forbidden(
                    "ATTENDING_DOCTOR_REQUIRED",
                    "Only the attending doctor may perform this clinical action.");
        }
    }

    private boolean hasAnyMeasurement(VitalSignRequest request) {
        return request.temperatureC != null
                || request.systolicMmhg != null
                || request.diastolicMmhg != null
                || request.pulseBpm != null
                || request.respiratoryRate != null
                || request.oxygenSaturation != null
                || request.weightKg != null
                || request.heightCm != null;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasSqlState(Throwable throwable, String expectedSqlState) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
