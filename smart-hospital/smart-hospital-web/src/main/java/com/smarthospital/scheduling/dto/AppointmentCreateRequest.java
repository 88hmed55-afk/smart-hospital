package com.smarthospital.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public class AppointmentCreateRequest {
    @NotNull public UUID branchId;
    @NotNull public UUID patientId;
    @NotNull public UUID doctorStaffId;
    @NotNull public UUID departmentId;
    @NotNull public Instant startsAt;
    @NotNull public Instant endsAt;
    @Size(max = 500) public String reason;
}
