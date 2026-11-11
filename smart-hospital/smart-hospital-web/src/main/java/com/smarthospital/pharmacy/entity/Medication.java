package com.smarthospital.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "medications")
public class Medication {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "generic_name", nullable = false, length = 160)
    private String genericName;

    @Column(name = "brand_name", length = 160)
    private String brandName;

    @Column(name = "strength", length = 80)
    private String strength;

    @Column(name = "dosage_form", length = 60)
    private String dosageForm;

    @Column(name = "drug_class", length = 80)
    private String drugClass;

    @Column(name = "route", length = 60)
    private String route;

    @Column(name = "dispensing_unit", nullable = false, length = 40)
    private String dispensingUnit;

    @Column(name = "is_controlled", nullable = false)
    private boolean controlled;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Medication() { }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getGenericName() { return genericName; }
    public String getBrandName() { return brandName; }
    public String getStrength() { return strength; }
    public String getDosageForm() { return dosageForm; }
    public String getDrugClass() { return drugClass; }
    public String getRoute() { return route; }
    public String getDispensingUnit() { return dispensingUnit; }
    public boolean isControlled() { return controlled; }
    public boolean isActive() { return active; }
}
