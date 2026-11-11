package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.Encounter;
import java.time.Instant;
import java.util.UUID;

public class EncounterResponse {
    public UUID id;
    public UUID branchId;
    public UUID patientId;
    public UUID appointmentId;
    public UUID departmentId;
    public UUID attendingDoctorId;
    public String encounterType;
    public String status;
    public String chiefComplaint;
    public Instant startedAt;
    public Instant endedAt;

    public static EncounterResponse from(Encounter e) {
        EncounterResponse r = new EncounterResponse();
        r.id = e.getId(); r.branchId = e.getBranchId(); r.patientId = e.getPatientId();
        r.appointmentId = e.getAppointmentId(); r.departmentId = e.getDepartmentId();
        r.attendingDoctorId = e.getAttendingDoctorId(); r.encounterType = e.getEncounterType().name();
        r.status = e.getStatus().name(); r.chiefComplaint = e.getChiefComplaint();
        r.startedAt = e.getStartedAt(); r.endedAt = e.getEndedAt();
        return r;
    }
}
