package com.smarthospital.analytics.rest;

import com.smarthospital.analytics.dto.DashboardResponse;
import com.smarthospital.analytics.dto.DecisionCardResponse;
import com.smarthospital.analytics.dto.DecisionCardStatusRequest;
import com.smarthospital.analytics.service.OperationalAnalyticsService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/analytics")
@Produces(MediaType.APPLICATION_JSON)
public class AnalyticsResource {
    @EJB
    private OperationalAnalyticsService analytics;

    @GET
    @Path("/dashboard")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public DashboardResponse dashboard(@QueryParam("branchId") UUID branchId) {
        return analytics.getDashboard(branchId);
    }

    @jakarta.ws.rs.POST
    @Path("/dashboard/refresh")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public DashboardResponse refreshDashboard(@QueryParam("branchId") UUID branchId) {
        return analytics.refreshOperationalSnapshot(branchId);
    }

    @GET
    @Path("/decision-cards")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public List<DecisionCardResponse> decisionCards(
            @QueryParam("branchId") UUID branchId,
            @QueryParam("openOnly") Boolean openOnly,
            @QueryParam("limit") Integer limit) {
        return analytics.listDecisionCards(branchId, openOnly == null || openOnly, limit == null ? 30 : limit);
    }

    @PATCH
    @Path("/decision-cards/{cardId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public DecisionCardResponse updateDecisionCardStatus(
            @PathParam("cardId") UUID cardId,
            @Valid DecisionCardStatusRequest request) {
        return analytics.changeDecisionCardStatus(cardId, request);
    }
}
