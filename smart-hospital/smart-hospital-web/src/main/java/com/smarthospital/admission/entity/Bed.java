package com.smarthospital.admission.entity;

import com.smarthospital.organization.entity.HospitalBranch;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(schema="hospital", name="beds")
public class Bed {
    @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id", nullable=false) private HospitalBranch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="room_id", nullable=false) private Room room;
    @Column(name="bed_number", nullable=false, length=24) private String bedNumber;
    @Column(name="bed_type", nullable=false, length=24) private String bedType;
    @Column(name="operational_status", nullable=false, length=24) private String operationalStatus;
    @Column(name="is_active", nullable=false) private boolean active;
    protected Bed() {}
    public UUID getId(){return id;} public UUID getBranchId(){return branch.getId();}
    public Room getRoom(){return room;} public String getBedNumber(){return bedNumber;}
    public String getBedType(){return bedType;} public String getOperationalStatus(){return operationalStatus;} public boolean isActive(){return active;}
}
