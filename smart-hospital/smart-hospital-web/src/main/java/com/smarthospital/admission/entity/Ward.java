package com.smarthospital.admission.entity;

import com.smarthospital.organization.entity.Department;
import com.smarthospital.organization.entity.HospitalBranch;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(schema = "hospital", name = "wards")
public class Ward {
    @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id", nullable=false) private HospitalBranch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="department_id", nullable=false) private Department department;
    @Column(name="ward_code", nullable=false, length=24) private String wardCode;
    @Column(name="ward_name", nullable=false, length=140) private String wardName;
    @Column(name="ward_type", nullable=false, length=24) private String wardType;
    @Column(name="is_active", nullable=false) private boolean active;
    protected Ward() {}
    public UUID getId(){return id;} public UUID getBranchId(){return branch.getId();}
    public String getWardCode(){return wardCode;} public String getWardName(){return wardName;}
    public String getWardType(){return wardType;} public boolean isActive(){return active;}
}
