package com.smarthospital.pharmacy.dto;

import com.smarthospital.pharmacy.entity.Prescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PrescriptionResponse {
    public UUID id;
    public UUID encounterId;
    public UUID prescriberStaffId;
    public UUID branchId;
    public String patientMrn;
    public String patientDisplayName;
    public String status;
    public Instant orderedAt;
    public List<PrescriptionItemResponse> items;
    /** Non-blocking safety findings plus any findings that were clinically overridden. */
    public List<String> safetyWarnings;

    public static PrescriptionResponse from(Prescription prescription, List<PrescriptionItemResponse> items) {
        PrescriptionResponse response = new PrescriptionResponse();
        response.id = prescription.getId();
        response.encounterId = prescription.getEncounterId();
        response.prescriberStaffId = prescription.getPrescriberStaffId();
        response.branchId = prescription.getBranchId();
        var patient = prescription.getEncounter().getPatient();
        response.patientMrn = patient.getMrn();
        response.patientDisplayName = String.join(" ",
                java.util.stream.Stream.of(patient.getFirstName(), patient.getMiddleName(), patient.getLastName())
                        .filter(value -> value != null && !value.isBlank())
                        .toList());
        response.status = prescription.getStatus().name();
        response.orderedAt = prescription.getOrderedAt();
        response.items = items;
        return response;
    }
}
