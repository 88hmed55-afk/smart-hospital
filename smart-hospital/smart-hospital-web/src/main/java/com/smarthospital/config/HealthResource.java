package com.smarthospital.config;

import com.smarthospital.config.dto.HealthReadinessResponse;
import com.smarthospital.config.service.HealthReadinessService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    @EJB private HealthReadinessService readinessService;

    /** Liveness: confirms that the authenticated application endpoint is responding. */
    @GET
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public Map<String, Object> health() {
        return Map.of("status", "UP", "application", "smart-hospital", "timestamp", Instant.now());
    }

    /** Readiness: checks MySQL and the configured JMS connection factory without exposing internals. */
    @GET
    @Path("/ready")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public Response readiness() {
        HealthReadinessResponse result = readinessService.check();
        return Response.status("UP".equals(result.status) ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(result)
                .build();
    }
}
