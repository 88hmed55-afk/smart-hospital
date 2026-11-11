package com.smarthospital.scheduling.service;

import com.smarthospital.common.error.BusinessException;
import com.smarthospital.common.api.PageResponse;
import com.smarthospital.scheduling.dto.AppointmentListItemResponse;
import com.smarthospital.security.domain.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.smarthospital.audit.service.AuditService;
import com.smarthospital.security.service.CurrentUserService;
import com.smarthospital.organization.entity.Department;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.patient.entity.Patient;
import com.smarthospital.scheduling.dto.AppointmentCreateRequest;
import com.smarthospital.scheduling.dto.AppointmentResponse;
import com.smarthospital.scheduling.entity.Appointment;
import com.smarthospital.scheduling.entity.AppointmentStatus;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AppointmentService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private AuditService auditService;

    @EJB
    private CurrentUserService currentUserService;
    public AppointmentResponse book(AppointmentCreateRequest request) {
        if (request == null) throw BusinessException.badRequest("REQUEST_BODY_REQUIRED", "An appointment request body is required.");
        if (request.endsAt == null || request.startsAt == null || !request.endsAt.isAfter(request.startsAt)) {
            throw BusinessException.badRequest("INVALID_APPOINTMENT_INTERVAL", "Appointment end time must be after start time.");
        }

        currentUserService.requireBranchAccess(request.branchId);
        currentUserService.requirePatientAccessInBranch(request.patientId, request.branchId);
        HospitalBranch branch = em.find(HospitalBranch.class, request.branchId);
        Patient patient = em.find(Patient.class, request.patientId);
        DoctorProfile doctor = em.find(DoctorProfile.class, request.doctorStaffId);
        Department department = em.find(Department.class, request.departmentId);

        if (branch == null) throw BusinessException.notFound("Hospital branch");
        if (patient == null) throw BusinessException.notFound("Patient");
        if (doctor == null || !doctor.isActive() || !doctor.getStaff().isActive()) {
            throw BusinessException.notFound("Active doctor profile");
        }
        if (department == null || !department.isActive()) throw BusinessException.notFound("Active department");

        if (!branch.getId().equals(doctor.getStaff().getBranch().getId())
                || !branch.getId().equals(department.getBranch().getId())) {
            throw BusinessException.badRequest("BRANCH_SCOPE_MISMATCH", "Doctor, department and appointment must belong to the same branch.");
        }
        if (doctor.getStaff().getDepartment() == null
                || !doctor.getStaff().getDepartment().getId().equals(department.getId())) {
            throw BusinessException.badRequest("DOCTOR_DEPARTMENT_MISMATCH", "The selected doctor is not assigned to the selected department.");
        }

        // MySQL has no PostgreSQL-style GiST exclusion constraint. Serialize bookings per doctor,
        // then check interval overlap in the same JTA transaction before persisting the appointment.
        em.find(DoctorProfile.class, doctor.getStaffId(), LockModeType.PESSIMISTIC_WRITE);
        Long overlapCount = em.createQuery("""
                SELECT COUNT(a) FROM Appointment a
                 WHERE a.doctor = :doctor
                   AND a.status IN :activeStatuses
                   AND a.startsAt < :requestedEnd
                   AND a.endsAt > :requestedStart
                """, Long.class)
                .setParameter("doctor", doctor)
                .setParameter("activeStatuses", List.of(AppointmentStatus.BOOKED,
                        AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN))
                .setParameter("requestedStart", request.startsAt)
                .setParameter("requestedEnd", request.endsAt)
                .getSingleResult();
        if (overlapCount > 0) {
            throw BusinessException.conflict("DOCTOR_TIME_CONFLICT", "The doctor already has an overlapping appointment.");
        }
        Appointment appointment = Appointment.book(request.branchId, patient, doctor, department,
                request.startsAt, request.endsAt, request.reason == null ? null : request.reason.trim());
        em.persist(appointment);
        em.flush();
        auditService.recordAction("APPOINTMENT_BOOKED", "APPOINTMENT", appointment.getId(), "appointment booked", request.branchId);
        return AppointmentResponse.from(appointment);
    }
    public AppointmentResponse findById(java.util.UUID appointmentId) {
        Appointment appointment = em.find(Appointment.class, appointmentId);
        if (appointment == null) throw BusinessException.notFound("Appointment");
        currentUserService.requireBranchAccess(appointment.getBranchId());
        currentUserService.requirePatientAccessInBranch(appointment.getPatientId(), appointment.getBranchId());
        auditService.recordAction("APPOINTMENT_VIEWED", "APPOINTMENT", appointment.getId(), "appointment detail view", appointment.getBranchId());
        return AppointmentResponse.from(appointment);
    }
    public AppointmentResponse checkIn(java.util.UUID appointmentId) {
        Appointment appointment = em.find(Appointment.class, appointmentId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (appointment == null) throw BusinessException.notFound("Appointment");
        currentUserService.requireBranchAccess(appointment.getBranchId());
        currentUserService.requirePatientAccessInBranch(appointment.getPatientId(), appointment.getBranchId());
        try {
            appointment.checkIn();
        } catch (IllegalStateException ex) {
            throw BusinessException.conflict("APPOINTMENT_STATE_CONFLICT", "Only booked or confirmed appointments can be checked in.");
        }
        auditService.recordAction("APPOINTMENT_CHECKED_IN", "APPOINTMENT", appointment.getId(), "appointment checked in", appointment.getBranchId());
        return AppointmentResponse.from(appointment);
    }
    public PageResponse<AppointmentListItemResponse> search(UUID branchId, Instant from, Instant to, int offset, int limit) {
        if (branchId == null || from == null || to == null || !to.isAfter(from)) {
            throw BusinessException.badRequest("APPOINTMENT_SEARCH_RANGE_REQUIRED", "Branch and a valid appointment date range are required.");
        }
        currentUserService.requireBranchAccess(branchId);
        CurrentUser actor = currentUserService.requireCurrentUser();
        boolean privilegedSchedule = actor.roleCodes().stream()
                .anyMatch(role -> List.of("HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST").contains(role));
        String doctorFilter = privilegedSchedule ? "" : " and a.doctor.staffId = :doctorStaffId ";
        String access = " exists (select pba.id from PatientBranchAccess pba "
                + "where pba.patient = a.patient and pba.branch.id = :branchId and pba.revokedAt is null) ";
        String where = "a.branchId = :branchId and a.startsAt >= :from and a.startsAt < :to and " + access + doctorFilter;
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        var dataQuery = em.createQuery("select a from Appointment a "
                + "join fetch a.patient p join fetch a.doctor dp join fetch dp.staff sm "
                + "join fetch a.department d where " + where + " order by a.startsAt asc", Appointment.class)
                .setParameter("branchId", branchId).setParameter("from", from).setParameter("to", to)
                .setFirstResult(safeOffset).setMaxResults(safeLimit);
        var countQuery = em.createQuery("select count(a) from Appointment a where " + where, Long.class)
                .setParameter("branchId", branchId).setParameter("from", from).setParameter("to", to);
        if (!privilegedSchedule) {
            java.util.UUID doctorStaffId = currentUserService.requireDoctorStaffId();
            dataQuery.setParameter("doctorStaffId", doctorStaffId);
            countQuery.setParameter("doctorStaffId", doctorStaffId);
        }
        long total = countQuery.getSingleResult();
        List<AppointmentListItemResponse> items = dataQuery.getResultList().stream()
                .map(AppointmentListItemResponse::from).toList();
        auditService.recordAction("APPOINTMENT_SEARCH_EXECUTED", "APPOINTMENT", null,
                "branch appointment schedule query; total=" + total, branchId);
        return new PageResponse<>(items, total, safeOffset, safeLimit);
    }
}
