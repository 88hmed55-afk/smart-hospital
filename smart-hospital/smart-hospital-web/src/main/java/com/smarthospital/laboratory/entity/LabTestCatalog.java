package com.smarthospital.laboratory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "lab_test_catalog")
public class LabTestCatalog {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "specimen_type", length = 80)
    private String specimenType;

    @Column(name = "result_unit", length = 40)
    private String resultUnit;

    @Column(name = "reference_range", length = 120)
    private String referenceRange;

    @Column(name = "standard_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal standardPrice;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "expected_minutes")
    private Integer expectedMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected LabTestCatalog() { }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSpecimenType() { return specimenType; }
    public String getResultUnit() { return resultUnit; }
    public String getReferenceRange() { return referenceRange; }
    public BigDecimal getStandardPrice() { return standardPrice; }
    public String getCurrencyCode() { return currencyCode; }
    public Integer getExpectedMinutes() { return expectedMinutes; }
    public boolean isActive() { return active; }
}
