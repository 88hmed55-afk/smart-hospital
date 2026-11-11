package com.smarthospital.patient.rest;

import com.smarthospital.common.api.PageResponse;
import com.smarthospital.patient.dto.PatientRegistrationRequest;
import com.smarthospital.patient.dto.PatientSummaryResponse;
import com.smarthospital.patient.dto.PatientResponse;
import com.smarthospital.patient.dto.PatientUpdateRequest;
import com.smarthospital.patient.service.PatientService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.UUID;

@Path("/patients")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatientResource {
    @EJB
    private PatientService patientService;

    @POST
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST"})
    public Response register(@Valid PatientRegistrationRequest request, @Context UriInfo uriInfo) {
        PatientResponse created = patientService.register(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created).build();
    }

    @GET
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public PageResponse<PatientSummaryResponse> search(
            @Size(max = 100) @QueryParam("q") String query,
            @DefaultValue("0") @QueryParam("offset") int offset,
            @DefaultValue("20") @QueryParam("limit") int limit) {
        return patientService.search(query, offset, limit);
    }

    @GET
    @Path("/{patientId}")
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public PatientResponse find(@PathParam("patientId") UUID patientId) {
        return patientService.findById(patientId);
    }

    @PUT
    @Path("/{patientId}")
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST"})
    public PatientResponse update(@PathParam("patientId") UUID patientId, @Valid PatientUpdateRequest request) {
        return patientService.updateDemographics(patientId, request);
    }
}
