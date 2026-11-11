package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.EncounterDiagnosis;
import java.time.Instant;
import java.util.UUID;

public class EncounterDiagnosisResponse {
    public UUID id;
    public UUID diagnosisId;
    public String codeSystem;
    public String diagnosisCode;
    public String description;
    public boolean primary;
    public String clinicalStatus;
    public Instant recordedAt;

    public static EncounterDiagnosisResponse from(EncounterDiagnosis d) {
        EncounterDiagnosisResponse r=new EncounterDiagnosisResponse();
        r.id=d.getId(); r.diagnosisId=d.getDiagnosis().getId(); r.codeSystem=d.getDiagnosis().getCodeSystem();
        r.diagnosisCode=d.getDiagnosis().getDiagnosisCode(); r.description=d.getDiagnosis().getDescription();
        r.primary=d.isPrimary(); r.clinicalStatus=d.getClinicalStatus().name(); r.recordedAt=d.getRecordedAt();
        return r;
    }
}
