package com.smarthospital.laboratory.rest;

import com.smarthospital.laboratory.dto.LabOrderCreateRequest;
import com.smarthospital.laboratory.dto.LabOrderResponse;
import com.smarthospital.laboratory.service.LaboratoryService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.List;
import java.util.UUID;

@Path("/encounters/{encounterId}/lab-orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EncounterLaboratoryResource {
    @EJB
    private LaboratoryService laboratoryService;

    @POST
    @RequiresRoles({"DOCTOR"})
    public Response create(
            @PathParam("encounterId") UUID encounterId,
            @Valid LabOrderCreateRequest request,
            @Context UriInfo uriInfo) {
        LabOrderResponse created = laboratoryService.createOrder(encounterId, request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created)
                .build();
    }

    @GET
    @RequiresRoles({"DOCTOR"})
    public List<LabOrderResponse> list(@PathParam("encounterId") UUID encounterId) {
        return laboratoryService.listEncounterOrders(encounterId);
    }
}
