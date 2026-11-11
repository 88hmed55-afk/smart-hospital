package com.smarthospital.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "prescription_items")
public class PrescriptionItem {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false, updatable = false)
    private Medication medication;

    @Column(name = "dose", nullable = false, length = 80)
    private String dose;

    @Column(name = "route", length = 60)
    private String route;

    @Column(name = "frequency", nullable = false, length = 100)
    private String frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "quantity_prescribed", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityPrescribed;

    @Column(name = "instructions", length = 500)
    private String instructions;

    protected PrescriptionItem() { }

    public static PrescriptionItem create(Prescription prescription, Medication medication, String dose,
                                          String route, String frequency, Integer durationDays,
                                          BigDecimal quantityPrescribed, String instructions) {
        PrescriptionItem item = new PrescriptionItem();
        item.prescription = prescription;
        item.medication = medication;
        item.dose = dose;
        item.route = route;
        item.frequency = frequency;
        item.durationDays = durationDays;
        item.quantityPrescribed = quantityPrescribed;
        item.instructions = instructions;
        return item;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public Prescription getPrescription() { return prescription; }
    public UUID getPrescriptionId() { return prescription.getId(); }
    public Medication getMedication() { return medication; }
    public UUID getMedicationId() { return medication.getId(); }
    public String getDose() { return dose; }
    public String getRoute() { return route; }
    public String getFrequency() { return frequency; }
    public Integer getDurationDays() { return durationDays; }
    public BigDecimal getQuantityPrescribed() { return quantityPrescribed; }
    public String getInstructions() { return instructions; }
}
