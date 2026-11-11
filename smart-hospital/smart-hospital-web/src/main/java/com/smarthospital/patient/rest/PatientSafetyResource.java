package com.smarthospital.patient.rest;

import com.smarthospital.patient.dto.AllergyCreateRequest;
import com.smarthospital.patient.dto.AllergyResponse;
import com.smarthospital.patient.dto.ConsentCreateRequest;
import com.smarthospital.patient.dto.ConsentResponse;
import com.smarthospital.patient.service.PatientSafetyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

/** Allergy charting and consent management for a patient. */
@Path("/patients/{patientId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PatientSafetyResource {

    @EJB
    private PatientSafetyService service;

    @GET
    @Path("/allergies")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public List<AllergyResponse> allergies(@PathParam("patientId") UUID patientId) {
        return service.listAllergies(patientId);
    }

    @POST
    @Path("/allergies")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public Response recordAllergy(@PathParam("patientId") UUID patientId,
                                  @Valid AllergyCreateRequest request,
                                  @Context UriInfo uriInfo) {
        AllergyResponse created = service.recordAllergy(patientId, request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created).build();
    }

    @POST
    @Path("/allergies/{allergyId}/refute")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "DOCTOR", "NURSE"})
    public AllergyResponse refuteAllergy(@PathParam("patientId") UUID patientId,
                                         @PathParam("allergyId") UUID allergyId,
                                         @QueryParam("reason") String reason) {
        return service.refuteAllergy(patientId, allergyId, reason);
    }

    @GET
    @Path("/consents")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public List<ConsentResponse> consents(@PathParam("patientId") UUID patientId) {
        return service.listConsents(patientId);
    }

    @POST
    @Path("/consents")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public Response recordConsent(@PathParam("patientId") UUID patientId,
                                  @Valid ConsentCreateRequest request,
                                  @Context UriInfo uriInfo) {
        ConsentResponse created = service.recordConsent(patientId, request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created).build();
    }

    @POST
    @Path("/consents/{consentId}/revoke")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "DOCTOR", "NURSE"})
    public ConsentResponse revokeConsent(@PathParam("patientId") UUID patientId,
                                         @PathParam("consentId") UUID consentId,
                                         @QueryParam("reason") String reason) {
        return service.revokeConsent(patientId, consentId, reason);
    }
}
