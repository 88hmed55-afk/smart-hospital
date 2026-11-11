package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.EncounterType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class EncounterCreateRequest {
    @NotNull public UUID branchId;
    @NotNull public UUID patientId;
    @NotNull public UUID departmentId;
    public UUID appointmentId;
    @NotNull public EncounterType encounterType;
    @Size(max = 1000) public String chiefComplaint;
}
