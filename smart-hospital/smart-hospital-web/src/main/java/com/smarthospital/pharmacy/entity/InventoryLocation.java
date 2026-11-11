package com.smarthospital.pharmacy.entity;

import com.smarthospital.organization.entity.HospitalBranch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "inventory_locations")
public class InventoryLocation {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private HospitalBranch branch;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 140)
    private String name;

    @Column(name = "location_type", nullable = false, length = 20)
    private String locationType;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected InventoryLocation() { }

    public UUID getId() { return id; }
    public HospitalBranch getBranch() { return branch; }
    public UUID getBranchId() { return branch.getId(); }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLocationType() { return locationType; }
    public boolean isActive() { return active; }
}
