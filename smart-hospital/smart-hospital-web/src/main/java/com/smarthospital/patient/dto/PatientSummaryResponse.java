package com.smarthospital.patient.dto;

import com.smarthospital.patient.entity.Patient;
import java.time.LocalDate;
import java.util.UUID;

public class PatientSummaryResponse {
    public UUID id;
    public String mrn;
    public String firstName;
    public String middleName;
    public String lastName;
    public LocalDate dateOfBirth;
    public String sex;
    public String primaryPhone;
    public String status;

    public PatientSummaryResponse() { }

    public static PatientSummaryResponse from(Patient patient) {
        PatientSummaryResponse dto = new PatientSummaryResponse();
        dto.id = patient.getId();
        dto.mrn = patient.getMrn();
        dto.firstName = patient.getFirstName();
        dto.middleName = patient.getMiddleName();
        dto.lastName = patient.getLastName();
        dto.dateOfBirth = patient.getDateOfBirth();
        dto.sex = patient.getSex().name();
        dto.primaryPhone = patient.getPrimaryPhone();
        dto.status = patient.getPatientStatus().name();
        return dto;
    }
}
