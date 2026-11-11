package com.smarthospital.clinical.entity;

import com.smarthospital.organization.entity.StaffMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An immutable measurement event captured during an encounter. */
@Entity
@Table(schema = "hospital", name = "vital_signs")
public class VitalSign {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, updatable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "measured_by", nullable = false, updatable = false)
    private StaffMember measuredBy;

    @Column(name = "measured_at", nullable = false, updatable = false)
    private Instant measuredAt;

    @Column(name = "temperature_c", precision = 4, scale = 1, updatable = false)
    private BigDecimal temperatureC;

    @Column(name = "systolic_mmhg", updatable = false)
    private Integer systolicMmhg;

    @Column(name = "diastolic_mmhg", updatable = false)
    private Integer diastolicMmhg;

    @Column(name = "pulse_bpm", updatable = false)
    private Integer pulseBpm;

    @Column(name = "respiratory_rate", updatable = false)
    private Integer respiratoryRate;

    @Column(name = "oxygen_saturation", precision = 5, scale = 2, updatable = false)
    private BigDecimal oxygenSaturation;

    @Column(name = "weight_kg", precision = 6, scale = 2, updatable = false)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 6, scale = 2, updatable = false)
    private BigDecimal heightCm;

    @Column(name = "notes", length = 500, updatable = false)
    private String notes;

    protected VitalSign() {
    }

    public static VitalSign record(
            Encounter encounter,
            StaffMember staff,
            BigDecimal temperatureC,
            Integer systolic,
            Integer diastolic,
            Integer pulse,
            Integer respiratory,
            BigDecimal oxygenSaturation,
            BigDecimal weight,
            BigDecimal height,
            String notes) {
        VitalSign vitalSign = new VitalSign();
        vitalSign.encounter = encounter;
        vitalSign.measuredBy = staff;
        vitalSign.temperatureC = temperatureC;
        vitalSign.systolicMmhg = systolic;
        vitalSign.diastolicMmhg = diastolic;
        vitalSign.pulseBpm = pulse;
        vitalSign.respiratoryRate = respiratory;
        vitalSign.oxygenSaturation = oxygenSaturation;
        vitalSign.weightKg = weight;
        vitalSign.heightCm = height;
        vitalSign.notes = notes;
        return vitalSign;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (measuredAt == null) {
            measuredAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getEncounterId() { return encounter.getId(); }
    public UUID getMeasuredBy() { return measuredBy.getId(); }
    public Instant getMeasuredAt() { return measuredAt; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public Integer getSystolicMmhg() { return systolicMmhg; }
    public Integer getDiastolicMmhg() { return diastolicMmhg; }
    public Integer getPulseBpm() { return pulseBpm; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public BigDecimal getOxygenSaturation() { return oxygenSaturation; }
    public BigDecimal getWeightKg() { return weightKg; }
    public BigDecimal getHeightCm() { return heightCm; }
    public String getNotes() { return notes; }
}
