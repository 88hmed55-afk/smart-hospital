package com.smarthospital.scheduling.dto;

import com.smarthospital.scheduling.entity.Appointment;
import java.time.Instant;
import java.util.UUID;

/** Branch-scoped appointment list row; excludes clinical notes and diagnoses. */
public class AppointmentListItemResponse {
    public UUID id;
    public UUID branchId;
    public UUID patientId;
    public String patientMrn;
    public String patientName;
    public UUID doctorStaffId;
    public String doctorName;
    public UUID departmentId;
    public String departmentName;
    public Instant startsAt;
    public Instant endsAt;
    public String reason;
    public String status;

    public AppointmentListItemResponse() { }

    public static AppointmentListItemResponse from(Appointment appointment) {
        AppointmentListItemResponse response = new AppointmentListItemResponse();
        response.id = appointment.getId();
        response.branchId = appointment.getBranchId();
        response.patientId = appointment.getPatient().getId();
        response.patientMrn = appointment.getPatient().getMrn();
        response.patientName = appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName();
        response.doctorStaffId = appointment.getDoctor().getStaff().getId();
        response.doctorName = appointment.getDoctor().getStaff().getFirstName() + " " + appointment.getDoctor().getStaff().getLastName();
        response.departmentId = appointment.getDepartment().getId();
        response.departmentName = appointment.getDepartment().getName();
        response.startsAt = appointment.getStartsAt();
        response.endsAt = appointment.getEndsAt();
        response.reason = appointment.getReason();
        response.status = appointment.getStatus().name();
        return response;
    }
}
