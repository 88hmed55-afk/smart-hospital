package com.smarthospital.security.rest;

import com.smarthospital.security.auth.RequiresRoles;
import com.smarthospital.security.dto.CurrentSessionResponse;
import com.smarthospital.security.dto.SecurityMetricsResponse;
import com.smarthospital.security.service.CurrentUserService;
import com.smarthospital.security.service.SecurityMetricsService;
import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/** Identity and security-posture endpoints for the authenticated caller. */
@Path("/security")
@Produces(MediaType.APPLICATION_JSON)
public class SecurityResource {
    @EJB
    private CurrentUserService currentUsers;

    @EJB
    private SecurityMetricsService securityMetrics;

    @GET
    @Path("/me")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public CurrentSessionResponse currentSession() {
        return CurrentSessionResponse.from(currentUsers.requireCurrentUser());
    }

    /** Aggregate, non-PHI security metrics for monitoring and the admin dashboard. */
    @GET
    @Path("/metrics")
    @RequiresRoles({"HOSPITAL_ADMIN"})
    public SecurityMetricsResponse metrics(@Context HttpServletRequest request) {
        return securityMetrics.current(request != null && request.isSecure());
    }
}
