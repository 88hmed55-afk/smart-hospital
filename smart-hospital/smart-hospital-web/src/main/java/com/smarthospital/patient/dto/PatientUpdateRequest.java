package com.smarthospital.patient.dto;

import com.smarthospital.patient.entity.PatientSex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class PatientUpdateRequest {
    @NotBlank @Size(max = 80) public String firstName;
    @Size(max = 80) public String middleName;
    @NotBlank @Size(max = 80) public String lastName;
    @PastOrPresent public LocalDate dateOfBirth;
    @NotNull public PatientSex sex;
    @Pattern(regexp = "A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-", message = "Blood group must be a recognized ABO/Rh value.") public String bloodGroup;
    @Size(max = 32) public String primaryPhone;
    @Email @Size(max = 254) public String email;
    @Size(max = 500) public String addressText;
    @Size(max = 8) public String preferredLanguage = "ar";
}
