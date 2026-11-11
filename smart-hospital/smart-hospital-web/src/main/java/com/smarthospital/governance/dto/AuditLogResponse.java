package com.smarthospital.governance.dto;

import java.time.Instant;
import java.util.UUID;

public class AuditLogResponse {
    public UUID id;
    public String actorUsername;
    public UUID branchId;
    public String branchName;
    public String actionCode;
    public String resourceType;
    public UUID resourceId;
    public String outcome;
    public Instant occurredAt;

    public AuditLogResponse() { }
}
