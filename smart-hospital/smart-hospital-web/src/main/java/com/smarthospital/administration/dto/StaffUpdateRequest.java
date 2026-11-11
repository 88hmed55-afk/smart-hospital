package com.smarthospital.administration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Full replacement of editable staff details; employee number and home branch remain immutable. */
public class StaffUpdateRequest {
    public UUID departmentId;
    @NotBlank @Size(max = 80) public String firstName;
    @NotBlank @Size(max = 80) public String lastName;
    @Email @Size(max = 254) public String workEmail;
    @Size(max = 32) public String phoneNumber;
    @Size(max = 100) public String jobTitle;
    @NotNull public String employmentStatus;
}
