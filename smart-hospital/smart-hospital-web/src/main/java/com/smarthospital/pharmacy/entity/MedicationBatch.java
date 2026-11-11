package com.smarthospital.pharmacy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "medication_batches")
public class MedicationBatch {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "batch_number", nullable = false, length = 80)
    private String batchNumber;

    @Column(name = "manufacturer", length = 160)
    private String manufacturer;

    @Column(name = "manufactured_on")
    private LocalDate manufacturedOn;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    protected MedicationBatch() { }

    public static MedicationBatch received(Medication medication, String batchNumber, String manufacturer,
                                           LocalDate manufacturedOn, LocalDate expiresOn) {
        MedicationBatch batch = new MedicationBatch();
        batch.id = UUID.randomUUID();
        batch.medication = medication;
        batch.batchNumber = batchNumber;
        batch.manufacturer = manufacturer;
        batch.manufacturedOn = manufacturedOn;
        batch.expiresOn = expiresOn;
        return batch;
    }

    @PrePersist
    void prePersist() { if (id == null) id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public Medication getMedication() { return medication; }
    public UUID getMedicationId() { return medication.getId(); }
    public String getBatchNumber() { return batchNumber; }
    public String getManufacturer() { return manufacturer; }
    public LocalDate getManufacturedOn() { return manufacturedOn; }
    public LocalDate getExpiresOn() { return expiresOn; }
}
