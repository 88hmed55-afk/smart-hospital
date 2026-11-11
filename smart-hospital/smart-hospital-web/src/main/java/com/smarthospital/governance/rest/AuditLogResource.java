package com.smarthospital.governance.rest;

import com.smarthospital.common.api.PageResponse;
import com.smarthospital.governance.dto.AuditIntegrityReport;
import com.smarthospital.governance.dto.AuditLogResponse;
import com.smarthospital.governance.service.AuditIntegrityService;
import com.smarthospital.governance.service.AuditQueryService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
public class AuditLogResource {
    @EJB private AuditQueryService auditQueryService;
    @EJB private AuditIntegrityService auditIntegrityService;

    /** Re-computes the tamper-evident audit hash chain. */
    @GET
    @Path("/integrity")
    @RequiresRoles({"HOSPITAL_ADMIN"})
    public AuditIntegrityReport integrity() {
        return auditIntegrityService.verify();
    }

    @GET
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public PageResponse<AuditLogResponse> search(
            @QueryParam("branchId") UUID branchId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("actionCode") String actionCode,
            @QueryParam("resourceType") String resourceType,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit) {
        return auditQueryService.search(branchId, from, to, actionCode, resourceType,
                offset == null ? 0 : offset, limit == null ? 30 : limit);
    }
}
