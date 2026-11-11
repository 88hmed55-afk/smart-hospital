package com.smarthospital.admission.entity;

import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.patient.entity.Patient;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(schema="hospital", name="admissions")
public class Admission {
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id @Column(name="id", nullable=false, updatable=false) private UUID id;
    @Column(name="branch_id", nullable=false, updatable=false) private UUID branchId;
    @Column(name="admission_number", nullable=false, updatable=false, length=40) private String admissionNumber;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="patient_id", nullable=false, updatable=false) private Patient patient;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="encounter_id", nullable=false, unique=true, updatable=false) private Encounter encounter;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="attending_doctor_id", nullable=false, updatable=false) private DoctorProfile attendingDoctor;
    @Enumerated(EnumType.STRING) @Column(name="admission_type", nullable=false, length=20) private AdmissionType admissionType;
    @Enumerated(EnumType.STRING) @Column(name="admission_status", nullable=false, length=20) private AdmissionStatus admissionStatus;
    @Column(name="admission_reason", length=1000) private String admissionReason;
    @Column(name="admitted_at", nullable=false, updatable=false) private Instant admittedAt;
    @Column(name="admitted_by", nullable=false, updatable=false) private UUID admittedBy;
    @Column(name="discharged_at") private Instant dischargedAt;
    @Column(name="discharged_by") private UUID dischargedBy;
    @Column(name="discharge_disposition", length=32) private String dischargeDisposition;
    @Column(name="discharge_summary") private String dischargeSummary;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected Admission() {}
    public static Admission admit(UUID branchId, String number, Patient patient, Encounter encounter, DoctorProfile doctor, AdmissionType type, String reason, UUID admittedBy) {
        Admission a=new Admission(); a.id=UUID.randomUUID(); a.branchId=branchId; a.admissionNumber=number; a.patient=patient; a.encounter=encounter; a.attendingDoctor=doctor; a.admissionType=type; a.admissionStatus=AdmissionStatus.ADMITTED; a.admissionReason=reason; a.admittedBy=admittedBy; return a;
    }
    public void discharge(UUID userId, String disposition, String summary, Instant at) {
        if(admissionStatus!=AdmissionStatus.ADMITTED) throw new IllegalStateException("Only an admitted patient can be discharged.");
        admissionStatus=AdmissionStatus.DISCHARGED; dischargedBy=userId; dischargedAt=at; dischargeDisposition=disposition; dischargeSummary=summary;
    }
    public void cancel(UUID userId, Instant at) { if(admissionStatus!=AdmissionStatus.ADMITTED) throw new IllegalStateException("Only an active admission can be cancelled."); admissionStatus=AdmissionStatus.CANCELLED; dischargedBy=userId; dischargedAt=at; }
    @PrePersist void prePersist(){ Instant now=Instant.now(); if(id==null)id=UUID.randomUUID(); if(admittedAt==null)admittedAt=now; if(createdAt==null)createdAt=now; updatedAt=now; }
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getBranchId(){return branchId;} public String getAdmissionNumber(){return admissionNumber;}
    public Patient getPatient(){return patient;} public UUID getPatientId(){return patient.getId();} public String getPatientMrn(){return patient.getMrn();}
    public String getPatientName(){return patient.getFirstName()+" "+patient.getLastName();}
    public Encounter getEncounter(){return encounter;} public UUID getEncounterId(){return encounter.getId();}
    public UUID getAttendingDoctorId(){return attendingDoctor.getStaffId();} public String getAttendingDoctorName(){return attendingDoctor.getStaff().getFirstName()+" "+attendingDoctor.getStaff().getLastName();}
    public AdmissionType getAdmissionType(){return admissionType;} public AdmissionStatus getAdmissionStatus(){return admissionStatus;}
    public String getAdmissionReason(){return admissionReason;} public Instant getAdmittedAt(){return admittedAt;} public Instant getDischargedAt(){return dischargedAt;}
    public String getDischargeDisposition(){return dischargeDisposition;} public String getDischargeSummary(){return dischargeSummary;}
}
