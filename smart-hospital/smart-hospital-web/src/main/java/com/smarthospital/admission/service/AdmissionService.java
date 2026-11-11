package com.smarthospital.admission.service;

import com.smarthospital.admission.dto.AdmissionCreateRequest;
import com.smarthospital.admission.dto.AdmissionResponse;
import com.smarthospital.admission.dto.BedAssignRequest;
import com.smarthospital.admission.dto.BedResponse;
import com.smarthospital.admission.dto.DischargeRequest;
import com.smarthospital.admission.entity.Admission;
import com.smarthospital.admission.entity.AdmissionStatus;
import com.smarthospital.admission.entity.AdmissionType;
import com.smarthospital.admission.entity.Bed;
import com.smarthospital.admission.entity.BedAssignment;
import com.smarthospital.admission.entity.BedAssignmentStatus;
import com.smarthospital.admission.entity.Room;
import com.smarthospital.audit.service.AuditService;
import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.clinical.entity.EncounterStatus;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.patient.entity.Patient;
import com.smarthospital.patient.entity.PatientBranchAccess;
import com.smarthospital.patient.entity.PatientStatus;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AdmissionService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService users;

    @EJB
    private AuditService audit;
    public AdmissionResponse admit(AdmissionCreateRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "An admission request is required.");
        }
        if (request.branchId == null || request.encounterId == null || request.admissionType == null) {
            throw BusinessException.badRequest("ADMISSION_CONTEXT_REQUIRED", "Branch, encounter and admission type are required.");
        }

        CurrentUser actor = users.requireCurrentUser();
        users.requireBranchAccess(request.branchId);

        Encounter encounter = em.find(Encounter.class, request.encounterId, LockModeType.PESSIMISTIC_WRITE);
        if (encounter == null) {
            throw BusinessException.notFound("Encounter");
        }
        if (!encounter.getBranchId().equals(request.branchId)) {
            throw BusinessException.badRequest("ADMISSION_BRANCH_MISMATCH", "Encounter and admission must belong to the same branch.");
        }
        users.requirePatientAccessInBranch(encounter.getPatientId(), request.branchId);

        if (encounter.getStatus() == EncounterStatus.CANCELLED || encounter.getStatus() == EncounterStatus.COMPLETED) {
            throw BusinessException.conflict("ENCOUNTER_NOT_ACTIVE", "Only an active encounter can be admitted.");
        }
        if (encounter.getEncounterType() != com.smarthospital.clinical.entity.EncounterType.INPATIENT
                && encounter.getEncounterType() != com.smarthospital.clinical.entity.EncounterType.EMERGENCY) {
            throw BusinessException.badRequest("ENCOUNTER_TYPE_NOT_ADMITTABLE", "Admission requires an INPATIENT or EMERGENCY encounter.");
        }
        if (actor.roleCodes().contains("DOCTOR")
                && !encounter.getAttendingDoctorId().equals(users.requireDoctorStaffId())) {
            throw BusinessException.forbidden("ATTENDING_DOCTOR_REQUIRED", "Only the attending doctor may initiate this admission.");
        }

        HospitalBranch branch = em.find(HospitalBranch.class, request.branchId);
        if (branch == null || !branch.isActive()) {
            throw BusinessException.notFound("Active branch");
        }

        Patient patient = encounter.getPatient();
        if (patient.getPatientStatus() != PatientStatus.ACTIVE) {
            throw BusinessException.conflict("PATIENT_NOT_ACTIVE", "An inactive patient record cannot receive a new admission.");
        }

        DoctorProfile doctor = em.find(DoctorProfile.class, encounter.getAttendingDoctorId());
        if (doctor == null || !doctor.isActive() || !doctor.getStaff().isActive()) {
            throw BusinessException.conflict("ACTIVE_ATTENDING_DOCTOR_REQUIRED", "The encounter must have an active attending doctor.");
        }

        Long activeAdmissions = em.createQuery(
                        "select count(a) from Admission a where a.branchId=:branch and a.patient.id=:patient and a.admissionStatus=:status",
                        Long.class)
                .setParameter("branch", request.branchId)
                .setParameter("patient", patient.getId())
                .setParameter("status", AdmissionStatus.ADMITTED)
                .getSingleResult();
        if (activeAdmissions > 0) {
            throw BusinessException.conflict("PATIENT_ALREADY_ADMITTED", "The patient already has an active admission in this branch.");
        }

        String admissionNumber = generatedNumber("ADM");
        Admission admission = Admission.admit(
                request.branchId,
                admissionNumber,
                patient,
                encounter,
                doctor,
                request.admissionType,
                clean(request.admissionReason),
                actor.userId());
        em.persist(admission);
        em.flush();

        if (request.initialBedId != null) {
            assignBedInternal(admission, request.initialBedId, actor, clean(request.bedAssignmentReason));
        }

        audit.recordAction("ADMISSION_CREATED", "ADMISSION", admission.getId(), "patient admission created", request.branchId);
        return toResponse(admission);
    }
    public List<BedResponse> listBeds(UUID branchId, UUID wardId, boolean availableOnly) {
        if (branchId == null) {
            throw BusinessException.badRequest("BRANCH_REQUIRED", "A hospital branch is required.");
        }
        users.requireBranchAccess(branchId);

        String jpql = "select b from Bed b join fetch b.room r join fetch r.ward w "
                + "where b.branch.id=:branch and b.active=true and w.active=true and r.active=true ";
        if (wardId != null) {
            jpql += "and w.id=:ward ";
        }
        jpql += "order by w.wardName,r.roomNumber,b.bedNumber";

        var query = em.createQuery(jpql, Bed.class).setParameter("branch", branchId);
        if (wardId != null) {
            query.setParameter("ward", wardId);
        }
        List<Bed> beds = query.getResultList();
        if (beds.isEmpty()) {
            return List.of();
        }

        List<UUID> bedIds = beds.stream().map(Bed::getId).toList();
        List<UUID> occupiedIds = em.createQuery(
                        "select distinct x.bed.id from BedAssignment x where x.bed.id in :ids and x.status=:status",
                        UUID.class)
                .setParameter("ids", bedIds)
                .setParameter("status", BedAssignmentStatus.ACTIVE)
                .getResultList();
        Set<UUID> occupiedSet = new HashSet<>(occupiedIds);

        return beds.stream()
                .map(bed -> bedResponse(bed, occupiedSet.contains(bed.getId())))
                .filter(bed -> !availableOnly || bed.available)
                .toList();
    }
    public AdmissionResponse assignBed(UUID admissionId, BedAssignRequest request) {
        if (request == null || request.bedId == null) {
            throw BusinessException.badRequest("BED_REQUIRED", "A target bed is required.");
        }

        Admission admission = requireAdmissionForUpdate(admissionId);
        requireAdmissionActorScope(admission);
        if (admission.getAdmissionStatus() != AdmissionStatus.ADMITTED) {
            throw BusinessException.conflict("ADMISSION_NOT_ACTIVE", "Only an active admission can receive a bed assignment.");
        }

        CurrentUser actor = users.requireCurrentUser();
        assignBedInternal(admission, request.bedId, actor, clean(request.reason));
        audit.recordAction("BED_ASSIGNED", "ADMISSION", admission.getId(), "bed assigned", admission.getBranchId());
        return toResponse(admission);
    }
    public AdmissionResponse transferBed(UUID admissionId, BedAssignRequest request) {
        if (request == null || request.bedId == null) {
            throw BusinessException.badRequest("BED_REQUIRED", "A target bed is required.");
        }

        Admission admission = requireAdmissionForUpdate(admissionId);
        requireAdmissionActorScope(admission);
        if (admission.getAdmissionStatus() != AdmissionStatus.ADMITTED) {
            throw BusinessException.conflict("ADMISSION_NOT_ACTIVE", "Only active admissions can be transferred.");
        }

        CurrentUser actor = users.requireCurrentUser();
        Bed target = lockEligibleBed(admission.getBranchId(), request.bedId);
        BedAssignment current = findCurrentAssignment(admission.getId(), true);
        if (current == null) {
            throw BusinessException.conflict("CURRENT_BED_NOT_ASSIGNED", "The admission has no current bed to transfer.");
        }
        if (current.getBedId().equals(target.getId())) {
            throw BusinessException.conflict("SAME_BED_TRANSFER", "The target bed is already assigned to this admission.");
        }

        Long targetOccupied = activeAssignmentsForBed(target.getId());
        if (targetOccupied > 0) {
            throw BusinessException.conflict("BED_ALREADY_OCCUPIED", "The selected target bed is already occupied.");
        }

        Instant now = Instant.now();
        current.release(actor.userId(), now, false);
        em.persist(BedAssignment.assign(admission, target, actor.userId(), clean(request.reason), now));
        em.flush();

        audit.recordAction("BED_TRANSFERRED", "ADMISSION", admission.getId(), "bed transfer", admission.getBranchId());
        return toResponse(admission);
    }
    public AdmissionResponse discharge(UUID admissionId, DischargeRequest request) {
        if (request == null || request.disposition == null || request.disposition.isBlank()) {
            throw BusinessException.badRequest("DISCHARGE_REQUEST_REQUIRED", "A discharge disposition is required.");
        }
        String disposition = request.disposition.trim().toUpperCase();
        if (!List.of("HOME", "TRANSFERRED", "AMA", "DECEASED", "OTHER").contains(disposition)) {
            throw BusinessException.badRequest("DISCHARGE_DISPOSITION_INVALID", "Unsupported discharge disposition.");
        }

        Admission admission = requireAdmissionForUpdate(admissionId);
        requireAdmissionActorScope(admission);
        if (admission.getAdmissionStatus() != AdmissionStatus.ADMITTED) {
            throw BusinessException.conflict("ADMISSION_NOT_ACTIVE", "Only an active admission can be discharged.");
        }

        CurrentUser actor = users.requireCurrentUser();
        Instant now = Instant.now();
        admission.discharge(actor.userId(), disposition, clean(request.summary), now);
        BedAssignment current = findCurrentAssignment(admission.getId(), true);
        if (current != null) {
            current.release(actor.userId(), now, false);
        }
        em.flush();

        audit.recordAction("PATIENT_DISCHARGED", "ADMISSION", admission.getId(), "admission discharged", admission.getBranchId());
        return toResponse(admission);
    }
    public List<AdmissionResponse> listActive(UUID branchId, int limit) {
        if (branchId == null) {
            throw BusinessException.badRequest("BRANCH_REQUIRED", "A hospital branch is required.");
        }
        users.requireBranchAccess(branchId);
        CurrentUser actor = users.requireCurrentUser();
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        String jpql = "select a from Admission a where a.branchId=:branch and a.admissionStatus=:status "
                + "and exists (select pba.id from PatientBranchAccess pba "
                + "where pba.patient=a.patient and pba.branch.id=a.branchId and pba.revokedAt is null) ";
        if (actor.roleCodes().contains("DOCTOR")) {
            jpql += "and a.attendingDoctor.staffId=:staff ";
        } else if (actor.roleCodes().contains("NURSE")) {
            jpql += "and exists (select ct.id from EncounterCareTeam ct "
                    + "where ct.encounter.id=a.encounter.id and ct.staff.id=:staff and ct.releasedAt is null) ";
        }
        jpql += "order by a.admittedAt desc";

        var query = em.createQuery(jpql, Admission.class)
                .setParameter("branch", branchId)
                .setParameter("status", AdmissionStatus.ADMITTED)
                .setMaxResults(safeLimit);
        if (actor.roleCodes().contains("DOCTOR") || actor.roleCodes().contains("NURSE")) {
            query.setParameter("staff", users.requireStaffId());
        }
        return responsesFor(query.getResultList());
    }

    private void assignBedInternal(Admission admission, UUID bedId, CurrentUser actor, String reason) {
        Bed bed = lockEligibleBed(admission.getBranchId(), bedId);
        Long currentBedCount = em.createQuery(
                        "select count(x) from BedAssignment x where x.admission.id=:id and x.status=:status",
                        Long.class)
                .setParameter("id", admission.getId())
                .setParameter("status", BedAssignmentStatus.ACTIVE)
                .getSingleResult();
        if (currentBedCount > 0) {
            throw BusinessException.conflict("ADMISSION_ALREADY_HAS_BED", "Transfer the patient instead of assigning a second active bed.");
        }
        if (activeAssignmentsForBed(bed.getId()) > 0) {
            throw BusinessException.conflict("BED_ALREADY_OCCUPIED", "The selected bed is already occupied.");
        }

        em.persist(BedAssignment.assign(admission, bed, actor.userId(), reason, Instant.now()));
        em.flush();
    }

    private Bed lockEligibleBed(UUID branchId, UUID bedId) {
        Bed bed = em.find(Bed.class, bedId, LockModeType.PESSIMISTIC_WRITE);
        if (bed == null || !bed.isActive()) {
            throw BusinessException.notFound("Active bed");
        }
        if (!bed.getBranchId().equals(branchId)) {
            throw BusinessException.badRequest("BED_BRANCH_MISMATCH", "Bed and admission must belong to the same branch.");
        }
        if (!"AVAILABLE".equals(bed.getOperationalStatus())) {
            throw BusinessException.conflict("BED_NOT_OPERATIONAL", "The bed is not operationally available.");
        }
        return bed;
    }

    private Long activeAssignmentsForBed(UUID bedId) {
        return em.createQuery(
                        "select count(x) from BedAssignment x where x.bed.id=:bed and x.status=:status",
                        Long.class)
                .setParameter("bed", bedId)
                .setParameter("status", BedAssignmentStatus.ACTIVE)
                .getSingleResult();
    }

    private Admission requireAdmissionForUpdate(UUID admissionId) {
        Admission admission = em.find(Admission.class, admissionId, LockModeType.PESSIMISTIC_WRITE);
        if (admission == null) {
            throw BusinessException.notFound("Admission");
        }
        users.requireBranchAccess(admission.getBranchId());
        users.requirePatientAccessInBranch(admission.getPatientId(), admission.getBranchId());
        return admission;
    }

    private void requireAdmissionActorScope(Admission admission) {
        CurrentUser actor = users.requireCurrentUser();
        if (actor.roleCodes().contains("DOCTOR")
                && !admission.getAttendingDoctorId().equals(users.requireDoctorStaffId())) {
            throw BusinessException.forbidden("ATTENDING_DOCTOR_REQUIRED", "Only the attending doctor may update this admission.");
        }
        if (actor.roleCodes().contains("NURSE")) {
            Long assignments = em.createQuery(
                            "select count(ct) from EncounterCareTeam ct "
                                    + "where ct.encounter.id=:encounter and ct.staff.id=:staff and ct.releasedAt is null",
                            Long.class)
                    .setParameter("encounter", admission.getEncounterId())
                    .setParameter("staff", users.requireStaffId())
                    .getSingleResult();
            if (assignments == 0) {
                throw BusinessException.forbidden("ACTIVE_CARE_TEAM_ASSIGNMENT_REQUIRED", "An active care-team assignment is required.");
            }
        }
    }

    private BedAssignment findCurrentAssignment(UUID admissionId, boolean lock) {
        var query = em.createQuery(
                        "select x from BedAssignment x join fetch x.bed b join fetch b.room r join fetch r.ward w "
                                + "where x.admission.id=:id and x.status=:status",
                        BedAssignment.class)
                .setParameter("id", admissionId)
                .setParameter("status", BedAssignmentStatus.ACTIVE);
        if (lock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return query.getResultStream().findFirst().orElse(null);
    }

    private AdmissionResponse toResponse(Admission admission) {
        return responsesFor(List.of(admission)).get(0);
    }

    private List<AdmissionResponse> responsesFor(List<Admission> admissions) {
        if (admissions.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = admissions.stream().map(Admission::getId).toList();
        List<BedAssignment> activeAssignments = em.createQuery(
                        "select x from BedAssignment x join fetch x.bed b join fetch b.room r join fetch r.ward w "
                                + "where x.admission.id in :ids and x.status=:status",
                        BedAssignment.class)
                .setParameter("ids", ids)
                .setParameter("status", BedAssignmentStatus.ACTIVE)
                .getResultList();

        Map<UUID, BedAssignment> byAdmission = new HashMap<>();
        for (BedAssignment assignment : activeAssignments) {
            byAdmission.put(assignment.getAdmissionId(), assignment);
        }
        return admissions.stream().map(admission -> {
            BedAssignment assignment = byAdmission.get(admission.getId());
            if (assignment == null) {
                return AdmissionResponse.from(admission, null, null);
            }
            Bed bed = assignment.getBed();
            Room room = bed.getRoom();
            String label = room.getWard().getWardName() + " / " + room.getRoomNumber() + " / " + bed.getBedNumber();
            return AdmissionResponse.from(admission, bed.getId(), label);
        }).toList();
    }

    private BedResponse bedResponse(Bed bed, boolean occupied) {
        Room room = bed.getRoom();
        BedResponse response = new BedResponse();
        response.id = bed.getId();
        response.branchId = bed.getBranchId();
        response.wardId = room.getWard().getId();
        response.wardName = room.getWard().getWardName();
        response.roomNumber = room.getRoomNumber();
        response.bedNumber = bed.getBedNumber();
        response.bedType = bed.getBedType();
        response.operationalStatus = bed.getOperationalStatus();
        response.occupied = occupied;
        response.available = !occupied && "AVAILABLE".equals(bed.getOperationalStatus());
        return response;
    }

    private String generatedNumber(String prefix) {
        return prefix + "-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
