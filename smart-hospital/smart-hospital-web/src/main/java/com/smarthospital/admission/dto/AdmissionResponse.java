package com.smarthospital.admission.dto;
import com.smarthospital.admission.entity.Admission;
import java.time.Instant;
import java.util.UUID;
public class AdmissionResponse {
 public UUID id; public UUID branchId; public String admissionNumber; public UUID patientId; public String patientMrn; public String patientName; public UUID encounterId; public UUID attendingDoctorId; public String attendingDoctorName; public String admissionType; public String status; public String reason; public Instant admittedAt; public Instant dischargedAt; public String dischargeDisposition; public String dischargeSummary; public UUID currentBedId; public String currentBedLabel;
 public static AdmissionResponse from(Admission a, UUID bedId, String bedLabel){ AdmissionResponse d=new AdmissionResponse();d.id=a.getId();d.branchId=a.getBranchId();d.admissionNumber=a.getAdmissionNumber();d.patientId=a.getPatientId();d.patientMrn=a.getPatientMrn();d.patientName=a.getPatientName();d.encounterId=a.getEncounterId();d.attendingDoctorId=a.getAttendingDoctorId();d.attendingDoctorName=a.getAttendingDoctorName();d.admissionType=a.getAdmissionType().name();d.status=a.getAdmissionStatus().name();d.reason=a.getAdmissionReason();d.admittedAt=a.getAdmittedAt();d.dischargedAt=a.getDischargedAt();d.dischargeDisposition=a.getDischargeDisposition();d.dischargeSummary=a.getDischargeSummary();d.currentBedId=bedId;d.currentBedLabel=bedLabel;return d;}
}
