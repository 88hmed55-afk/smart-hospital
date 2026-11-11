package com.smarthospital.laboratory.rest;

import com.smarthospital.laboratory.dto.LabOrderItemResponse;
import com.smarthospital.laboratory.dto.LabOrderResponse;
import com.smarthospital.laboratory.dto.LabResultCreateRequest;
import com.smarthospital.laboratory.dto.LabResultResponse;
import com.smarthospital.laboratory.dto.LabSpecimenRequest;
import com.smarthospital.laboratory.service.LaboratoryService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/lab-orders")
@Produces(MediaType.APPLICATION_JSON)
public class LaboratoryResource {
    @EJB
    private LaboratoryService laboratoryService;

    @GET
    @RequiresRoles({"LAB_TECHNICIAN", "LAB_SUPERVISOR"})
    public List<LabOrderResponse> list(
            @QueryParam("branchId") java.util.UUID branchId,
            @QueryParam("status") String status,
            @QueryParam("limit") Integer limit) {
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required");
        return laboratoryService.listBranchOrders(branchId, status, limit == null ? 30 : limit);
    }

    @POST
    @Path("/items/{itemId}/collect")
    @RequiresRoles({"LAB_TECHNICIAN", "LAB_SUPERVISOR"})
    public LabOrderItemResponse collect(
            @PathParam("itemId") UUID itemId,
            @Valid LabSpecimenRequest request) {
        return laboratoryService.collectSpecimen(itemId, request);
    }

    @POST
    @Path("/items/{itemId}/start-processing")
    @RequiresRoles({"LAB_TECHNICIAN"})
    public LabOrderItemResponse startProcessing(@PathParam("itemId") UUID itemId) {
        return laboratoryService.startProcessing(itemId);
    }

    @POST
    @Path("/items/{itemId}/result")
    @RequiresRoles({"LAB_TECHNICIAN"})
    public LabResultResponse recordResult(
            @PathParam("itemId") UUID itemId,
            @Valid LabResultCreateRequest request) {
        return laboratoryService.recordResult(itemId, request);
    }

    @POST
    @Path("/items/{itemId}/review-release")
    @RequiresRoles({"LAB_SUPERVISOR"})
    public LabResultResponse reviewAndRelease(@PathParam("itemId") UUID itemId) {
        return laboratoryService.reviewAndReleaseResult(itemId);
    }
}
