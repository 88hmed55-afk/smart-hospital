package com.smarthospital.pharmacy.rest;

import com.smarthospital.pharmacy.dto.DispenseCreateRequest;
import com.smarthospital.pharmacy.dto.DispenseResponse;
import com.smarthospital.pharmacy.dto.PrescriptionResponse;
import com.smarthospital.pharmacy.service.PharmacyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

@Path("/pharmacy/prescriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PharmacyPrescriptionResource {
    @EJB
    private PharmacyService pharmacyService;

    @GET
    @RequiresRoles({"PHARMACIST"})
    public List<PrescriptionResponse> list(
            @QueryParam("branchId") UUID branchId,
            @QueryParam("status") String status,
            @QueryParam("limit") Integer limit) {
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required");
        return pharmacyService.listBranchPrescriptions(branchId, status, limit == null ? 30 : limit);
    }

    @POST
    @Path("/{prescriptionId}/dispenses")
    @RequiresRoles({"PHARMACIST"})
    public Response dispense(
            @PathParam("prescriptionId") UUID prescriptionId,
            @QueryParam("branchId") UUID branchId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid DispenseCreateRequest request,
            @Context UriInfo uriInfo) {
        UUID locationId = request == null ? null : request.locationId;
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required");
        DispenseResponse created = pharmacyService.dispense(branchId, prescriptionId, locationId, idempotencyKey, request);
        return Response.created(uriInfo.getBaseUriBuilder()
                        .path("pharmacy/dispenses")
                        .path(created.id.toString())
                        .build())
                .entity(created)
                .build();
    }
}
