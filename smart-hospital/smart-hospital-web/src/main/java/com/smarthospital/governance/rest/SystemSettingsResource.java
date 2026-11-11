package com.smarthospital.governance.rest;

import com.smarthospital.governance.dto.SystemSettingResponse;
import com.smarthospital.governance.dto.SystemSettingUpdateRequest;
import com.smarthospital.governance.service.SystemSettingsService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/system/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SystemSettingsResource {
    @EJB private SystemSettingsService settingsService;

    @GET
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public List<SystemSettingResponse> list(@QueryParam("branchId") UUID branchId) {
        return settingsService.list(branchId);
    }

    @PUT
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public List<SystemSettingResponse> update(@Valid SystemSettingUpdateRequest request) {
        return settingsService.update(request);
    }
}
