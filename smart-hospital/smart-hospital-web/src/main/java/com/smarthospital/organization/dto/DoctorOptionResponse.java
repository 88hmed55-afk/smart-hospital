package com.smarthospital.organization.dto;

import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.StaffMember;
import java.util.UUID;

/** Minimal doctor selector projection for appointment booking. */
public class DoctorOptionResponse {
    public UUID staffId;
    public String employeeNumber;
    public String fullName;
    public UUID departmentId;
    public String departmentName;
    public String specialty;

    public DoctorOptionResponse() { }

    public static DoctorOptionResponse from(DoctorProfile doctor) {
        StaffMember staff = doctor.getStaff();
        DoctorOptionResponse response = new DoctorOptionResponse();
        response.staffId = staff.getId();
        response.employeeNumber = staff.getEmployeeNumber();
        response.fullName = staff.getFirstName() + " " + staff.getLastName();
        response.departmentId = staff.getDepartment() == null ? null : staff.getDepartment().getId();
        response.departmentName = staff.getDepartment() == null ? null : staff.getDepartment().getName();
        response.specialty = doctor.getSpecialty();
        return response;
    }
}
