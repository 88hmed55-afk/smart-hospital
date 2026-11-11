package com.smarthospital.patient.dto;

import com.smarthospital.patient.entity.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PatientResponse {
    public UUID id;
    public String mrn;
    public String firstName;
    public String middleName;
    public String lastName;
    public LocalDate dateOfBirth;
    public String sex;
    public String bloodGroup;
    public String primaryPhone;
    public String email;
    public String addressText;
    public String preferredLanguage;
    public String status;
    public Instant createdAt;
    public List<ContactResponse> contacts;
    public List<AllergyResponse> allergies;

    public PatientResponse() { }

    public static PatientResponse from(Patient patient) {
        PatientResponse dto = new PatientResponse();
        dto.id = patient.getId();
        dto.mrn = patient.getMrn();
        dto.firstName = patient.getFirstName();
        dto.middleName = patient.getMiddleName();
        dto.lastName = patient.getLastName();
        dto.dateOfBirth = patient.getDateOfBirth();
        dto.sex = patient.getSex().name();
        dto.bloodGroup = patient.getBloodGroup();
        dto.primaryPhone = patient.getPrimaryPhone();
        dto.email = patient.getEmail();
        dto.addressText = patient.getAddressText();
        dto.preferredLanguage = patient.getPreferredLanguage();
        dto.status = patient.getPatientStatus().name();
        dto.createdAt = patient.getCreatedAt();
        dto.contacts = patient.getContacts().stream().map(ContactResponse::from).toList();
        dto.allergies = patient.getAllergies().stream().map(AllergyResponse::from).toList();
        return dto;
    }

    public static class ContactResponse {
        public UUID id;
        public String type;
        public String name;
        public String relationship;
        public String phoneNumber;
        public String email;
        public boolean primary;
        public Instant createdAt;
        static ContactResponse from(com.smarthospital.patient.entity.PatientContact contact) {
            ContactResponse dto = new ContactResponse();
            dto.id = contact.getId();
            dto.type = contact.getContactType().name();
            dto.name = contact.getContactName();
            dto.relationship = contact.getRelationship();
            dto.phoneNumber = contact.getPhoneNumber();
            dto.email = contact.getEmail();
            dto.primary = contact.isPrimary();
            dto.createdAt = contact.getCreatedAt();
            return dto;
        }
    }

    public static class AllergyResponse {
        public UUID id;
        public String allergenName;
        public String reaction;
        public String severity;
        public String verificationStatus;
        public Instant recordedAt;
        static AllergyResponse from(com.smarthospital.patient.entity.PatientAllergy allergy) {
            AllergyResponse dto = new AllergyResponse();
            dto.id = allergy.getId();
            dto.allergenName = allergy.getAllergenName();
            dto.reaction = allergy.getReaction();
            dto.severity = allergy.getSeverity().name();
            dto.verificationStatus = allergy.getVerificationStatus().name();
            dto.recordedAt = allergy.getRecordedAt();
            return dto;
        }
    }
}
