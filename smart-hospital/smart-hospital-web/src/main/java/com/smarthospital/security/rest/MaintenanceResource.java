package com.smarthospital.security.rest;

import com.smarthospital.security.auth.RequiresRoles;
import com.smarthospital.security.dto.MaintenanceResult;
import com.smarthospital.security.service.SessionMaintenanceService;
import jakarta.ejb.EJB;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Operational maintenance endpoints. Restricted to HOSPITAL_ADMIN because they mutate
 * authentication state, even though they never read patient data.
 */
@Path("/system/maintenance")
@Produces(MediaType.APPLICATION_JSON)
public class MaintenanceResource {
    @EJB
    private SessionMaintenanceService maintenance;

    /** Prunes stale sessions and releases expired lockouts; safe to run any time. */
    @POST
    @Path("/cleanup")
    @RequiresRoles({"HOSPITAL_ADMIN"})
    public MaintenanceResult cleanup() {
        return maintenance.runCleanup();
    }
}
