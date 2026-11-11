package com.smarthospital.scheduling.entity;

import com.smarthospital.organization.entity.Department;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.patient.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "appointments")
public class Appointment {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_staff_id", nullable = false)
    private DoctorProfile doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() { }

    public static Appointment book(UUID branchId, Patient patient, DoctorProfile doctor, Department department,
                                   Instant startsAt, Instant endsAt, String reason) {
        Appointment appointment = new Appointment();
        appointment.branchId = branchId;
        appointment.patient = patient;
        appointment.doctor = doctor;
        appointment.department = department;
        appointment.startsAt = startsAt;
        appointment.endsAt = endsAt;
        appointment.reason = reason;
        appointment.status = AppointmentStatus.BOOKED;
        return appointment;
    }

    public void checkIn() {
        if (status != AppointmentStatus.BOOKED && status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only booked or confirmed appointments can be checked in.");
        }
        status = AppointmentStatus.CHECKED_IN;
    }

    public void completeAfterEncounter() {
        if (status != AppointmentStatus.CHECKED_IN) {
            throw new IllegalStateException("Only checked-in appointments can be completed by an encounter.");
        }
        status = AppointmentStatus.COMPLETED;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public Patient getPatient() { return patient; }
    public DoctorProfile getDoctor() { return doctor; }
    public Department getDepartment() { return department; }
    public UUID getPatientId() { return patient.getId(); }
    public UUID getDoctorStaffId() { return doctor.getStaffId(); }
    public UUID getDepartmentId() { return department.getId(); }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
