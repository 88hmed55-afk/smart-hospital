package com.smarthospital.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "doctor_profiles")
public class DoctorProfile {
    @Id
    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private StaffMember staff;

    @Column(name = "license_number", nullable = false, unique = true, length = 80)
    private String licenseNumber;

    @Column(name = "specialty", nullable = false, length = 120)
    private String specialty;

    @Column(name = "qualification", length = 160)
    private String qualification;

    @Column(name = "can_order_labs", nullable = false)
    private boolean canOrderLabs;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected DoctorProfile() { }

    public UUID getStaffId() { return staffId; }
    public StaffMember getStaff() { return staff; }
    public String getSpecialty() { return specialty; }
    public boolean canOrderLabs() { return canOrderLabs; }
    public boolean isActive() { return active; }
}
