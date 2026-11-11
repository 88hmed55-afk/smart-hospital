package com.smarthospital.administration.dto;

import java.time.LocalDate;
import java.util.UUID;

public class StaffResponse {
    public UUID id;
    public UUID branchId;
    public String branchName;
    public UUID departmentId;
    public String departmentName;
    public String employeeNumber;
    public String firstName;
    public String lastName;
    public String workEmail;
    public String phoneNumber;
    public String jobTitle;
    public String employmentStatus;
    public LocalDate hiredOn;
    public String linkedUsername;
}
