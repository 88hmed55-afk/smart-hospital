package com.smarthospital.clinical.entity;

import com.smarthospital.organization.entity.Department;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.patient.entity.Patient;
import com.smarthospital.scheduling.entity.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single clinical care episode for one patient at one hospital branch. */
@Entity
@Table(schema = "hospital", name = "encounters")
public class Encounter {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true, updatable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, updatable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attending_doctor_id", nullable = false, updatable = false)
    private DoctorProfile attendingDoctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_type", nullable = false, length = 20)
    private EncounterType encounterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EncounterStatus status;

    @Column(name = "chief_complaint", length = 1000)
    private String chiefComplaint;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected Encounter() {
    }

    public static Encounter open(
            UUID branchId,
            Patient patient,
            Appointment appointment,
            Department department,
            DoctorProfile doctor,
            EncounterType type,
            String chiefComplaint,
            UUID createdBy) {
        Encounter encounter = new Encounter();
        encounter.branchId = branchId;
        encounter.patient = patient;
        encounter.appointment = appointment;
        encounter.department = department;
        encounter.attendingDoctor = doctor;
        encounter.encounterType = type;
        encounter.status = EncounterStatus.IN_PROGRESS;
        encounter.chiefComplaint = chiefComplaint;
        encounter.createdBy = createdBy;
        return encounter;
    }

    public void complete() {
        if (status != EncounterStatus.OPEN && status != EncounterStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an active encounter can be completed.");
        }
        status = EncounterStatus.COMPLETED;
        endedAt = Instant.now();
    }

    public void cancel() {
        if (status == EncounterStatus.COMPLETED || status == EncounterStatus.CANCELLED) {
            throw new IllegalStateException("Encounter cannot be cancelled in its current state.");
        }
        status = EncounterStatus.CANCELLED;
        endedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == null) {
            status = EncounterStatus.OPEN;
        }
    }

    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public UUID getPatientId() { return patient.getId(); }
    public Patient getPatient() { return patient; }
    public UUID getAppointmentId() { return appointment == null ? null : appointment.getId(); }
    public UUID getDepartmentId() { return department.getId(); }
    public UUID getAttendingDoctorId() { return attendingDoctor.getStaffId(); }
    public EncounterType getEncounterType() { return encounterType; }
    public EncounterStatus getStatus() { return status; }
    public String getChiefComplaint() { return chiefComplaint; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Appointment getAppointment() { return appointment; }
}
