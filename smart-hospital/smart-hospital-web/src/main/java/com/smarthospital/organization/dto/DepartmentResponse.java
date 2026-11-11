package com.smarthospital.organization.dto;

import com.smarthospital.organization.entity.Department;
import java.util.UUID;

public class DepartmentResponse {
    public UUID id;
    public UUID branchId;
    public String code;
    public String name;
    public String departmentType;
    public boolean active;

    public DepartmentResponse() { }

    public static DepartmentResponse from(Department department) {
        DepartmentResponse dto = new DepartmentResponse();
        dto.id = department.getId();
        dto.branchId = department.getBranch().getId();
        dto.code = department.getCode();
        dto.name = department.getName();
        dto.departmentType = department.getDepartmentType();
        dto.active = department.isActive();
        return dto;
    }
}
