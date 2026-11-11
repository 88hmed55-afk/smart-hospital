package com.smarthospital.admission.dto;
import com.smarthospital.admission.entity.AdmissionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public class AdmissionCreateRequest {
 @NotNull public UUID branchId; @NotNull public UUID encounterId; @NotNull public AdmissionType admissionType;
 @Size(max=1000) public String admissionReason; public UUID initialBedId; @Size(max=500) public String bedAssignmentReason;
}
