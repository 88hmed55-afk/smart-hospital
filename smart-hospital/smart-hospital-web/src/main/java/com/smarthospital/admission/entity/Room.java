package com.smarthospital.admission.entity;

import com.smarthospital.organization.entity.HospitalBranch;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(schema="hospital", name="rooms")
public class Room {
    @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="branch_id", nullable=false) private HospitalBranch branch;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="ward_id", nullable=false) private Ward ward;
    @Column(name="room_number", nullable=false, length=24) private String roomNumber;
    @Column(name="room_type", nullable=false, length=24) private String roomType;
    @Column(name="floor_label", length=32) private String floorLabel;
    @Column(name="is_active", nullable=false) private boolean active;
    protected Room() {}
    public UUID getId(){return id;} public UUID getBranchId(){return branch.getId();}
    public Ward getWard(){return ward;} public String getRoomNumber(){return roomNumber;}
    public String getRoomType(){return roomType;} public String getFloorLabel(){return floorLabel;} public boolean isActive(){return active;}
}
