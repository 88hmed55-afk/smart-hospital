package com.smarthospital.scheduling.dto;

import com.smarthospital.scheduling.entity.Appointment;
import java.time.Instant;
import java.util.UUID;

public class AppointmentResponse {
    public UUID id;
    public UUID branchId;
    public UUID patientId;
    public UUID doctorStaffId;
    public UUID departmentId;
    public Instant startsAt;
    public Instant endsAt;
    public String reason;
    public String status;
    public Instant createdAt;

    public AppointmentResponse() { }

    public static AppointmentResponse from(Appointment appointment) {
        AppointmentResponse dto = new AppointmentResponse();
        dto.id = appointment.getId();
        dto.branchId = appointment.getBranchId();
        dto.patientId = appointment.getPatientId();
        dto.doctorStaffId = appointment.getDoctorStaffId();
        dto.departmentId = appointment.getDepartmentId();
        dto.startsAt = appointment.getStartsAt();
        dto.endsAt = appointment.getEndsAt();
        dto.reason = appointment.getReason();
        dto.status = appointment.getStatus().name();
        dto.createdAt = appointment.getCreatedAt();
        return dto;
    }
}
