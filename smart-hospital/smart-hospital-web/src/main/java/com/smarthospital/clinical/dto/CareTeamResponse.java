package com.smarthospital.clinical.dto;

import com.smarthospital.clinical.entity.EncounterCareTeam;
import java.time.Instant;
import java.util.UUID;

public class CareTeamResponse {
    public UUID id;
    public UUID encounterId;
    public UUID staffId;
    public String staffName;
    public String careRole;
    public UUID assignedBy;
    public Instant assignedAt;
    public Instant releasedAt;

    public static CareTeamResponse from(EncounterCareTeam assignment) {
        CareTeamResponse response = new CareTeamResponse();
        response.id = assignment.getId();
        response.encounterId = assignment.getEncounterId();
        response.staffId = assignment.getStaffId();
        response.staffName = assignment.getStaffName();
        response.careRole = assignment.getCareRole().name();
        response.assignedBy = assignment.getAssignedBy();
        response.assignedAt = assignment.getAssignedAt();
        response.releasedAt = assignment.getReleasedAt();
        return response;
    }
}
