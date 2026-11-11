package com.smarthospital.patient.dto;

import com.smarthospital.patient.entity.AllergySeverity;
import com.smarthospital.patient.entity.AllergyVerificationStatus;
import com.smarthospital.patient.entity.ContactType;
import com.smarthospital.patient.entity.PatientSex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PatientRegistrationRequest {
    /** Target branch; defaults to the caller default branch when omitted. */
    public java.util.UUID branchId;
    @NotBlank @Size(max = 32)
    public String mrn;
    @NotBlank @Size(max = 80)
    public String firstName;
    @Size(max = 80)
    public String middleName;
    @NotBlank @Size(max = 80)
    public String lastName;
    @PastOrPresent
    public LocalDate dateOfBirth;
    @NotNull
    public PatientSex sex;
    @Pattern(regexp = "A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-", message = "Blood group must be a recognized ABO/Rh value.")
    public String bloodGroup;
    @Size(max = 32)
    public String primaryPhone;
    @Email @Size(max = 254)
    public String email;
    @Size(max = 500)
    public String addressText;
    @Size(max = 8)
    public String preferredLanguage = "ar";
    @Valid @Size(max = 10)
    public List<@NotNull @Valid ContactInput> contacts = new ArrayList<>();
    @Valid @Size(max = 100)
    public List<@NotNull @Valid AllergyInput> allergies = new ArrayList<>();

    public static class ContactInput {
        @NotNull public ContactType contactType;
        @Size(max = 160) public String contactName;
        @Size(max = 60) public String relationship;
        @Size(max = 32) public String phoneNumber;
        @Email @Size(max = 254) public String email;
        public boolean primary;

        @AssertTrue(message = "A contact phone number or email address is required.")
        public boolean isContactMethodProvided() {
            return (phoneNumber != null && !phoneNumber.isBlank()) || (email != null && !email.isBlank());
        }
    }

    public static class AllergyInput {
        @NotBlank @Size(max = 160) public String allergenName;
        @Size(max = 500) public String reaction;
        public AllergySeverity severity = AllergySeverity.UNKNOWN;
        public AllergyVerificationStatus verificationStatus = AllergyVerificationStatus.UNVERIFIED;
    }
}
