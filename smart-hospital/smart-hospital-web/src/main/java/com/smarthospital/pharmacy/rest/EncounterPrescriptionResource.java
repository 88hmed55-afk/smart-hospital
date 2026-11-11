package com.smarthospital.pharmacy.rest;

import com.smarthospital.pharmacy.dto.PrescriptionCreateRequest;
import com.smarthospital.pharmacy.dto.PrescriptionResponse;
import com.smarthospital.pharmacy.service.PharmacyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/encounters/{encounterId}/prescriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EncounterPrescriptionResource {
    @EJB
    private PharmacyService pharmacyService;

    @POST
    @RequiresRoles({"DOCTOR"})
    public Response create(
            @PathParam("encounterId") UUID encounterId,
            @Valid PrescriptionCreateRequest request) {
        PrescriptionResponse created = pharmacyService.createPrescription(encounterId, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @RequiresRoles({"DOCTOR"})
    public List<PrescriptionResponse> list(@PathParam("encounterId") UUID encounterId) {
        return pharmacyService.listEncounterPrescriptions(encounterId);
    }
}
