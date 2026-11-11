package com.smarthospital.billing.dto;

import com.smarthospital.clinical.entity.Encounter;
import java.time.Instant;
import java.util.UUID;

public class BillableEncounterResponse {
    public UUID id;
    public UUID patientId;
    public String patientMrn;
    public String patientName;
    public String encounterType;
    public String status;
    public String chiefComplaint;
    public Instant startedAt;

    public static BillableEncounterResponse from(Encounter encounter) {
        BillableEncounterResponse response = new BillableEncounterResponse();
        response.id = encounter.getId();
        response.patientId = encounter.getPatientId();
        response.patientMrn = encounter.getPatient().getMrn();
        response.patientName = String.join(" ", java.util.stream.Stream.of(
                encounter.getPatient().getFirstName(), encounter.getPatient().getMiddleName(), encounter.getPatient().getLastName())
                .filter(value -> value != null && !value.isBlank()).toList());
        response.encounterType = encounter.getEncounterType().name();
        response.status = encounter.getStatus().name();
        response.chiefComplaint = encounter.getChiefComplaint();
        response.startedAt = encounter.getStartedAt();
        return response;
    }
}
