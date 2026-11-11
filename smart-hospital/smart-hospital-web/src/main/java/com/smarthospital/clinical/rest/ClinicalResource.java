package com.smarthospital.clinical.rest;

import com.smarthospital.clinical.dto.*;
import com.smarthospital.clinical.service.ClinicalService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.UUID;

@Path("/encounters")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClinicalResource {
    @EJB private ClinicalService clinicalService;

    @POST @RequiresRoles({"DOCTOR"})
    public Response open(@Valid EncounterCreateRequest request,@Context UriInfo uriInfo) {
        EncounterResponse created=clinicalService.openEncounter(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build()).entity(created).build();
    }
    @GET @Path("/{encounterId}") @RequiresRoles({"DOCTOR","NURSE"})
    public EncounterResponse find(@PathParam("encounterId") UUID id){return clinicalService.findEncounter(id);}
    @POST @Path("/{encounterId}/vitals") @RequiresRoles({"DOCTOR","NURSE"})
    public Response recordVitals(@PathParam("encounterId") UUID id,@Valid VitalSignRequest request){return Response.status(201).entity(clinicalService.recordVitals(id,request)).build();}
    @GET @Path("/{encounterId}/vitals") @RequiresRoles({"DOCTOR","NURSE"})
    public java.util.List<VitalSignResponse> listVitals(@PathParam("encounterId") UUID id){return clinicalService.listVitals(id);}
    @POST @Path("/{encounterId}/notes") @RequiresRoles({"DOCTOR","NURSE"})
    public Response createNote(@PathParam("encounterId") UUID id,@Valid ClinicalNoteCreateRequest request){return Response.status(201).entity(clinicalService.createNote(id,request)).build();}
    @GET @Path("/{encounterId}/notes") @RequiresRoles({"DOCTOR","NURSE"})
    public java.util.List<ClinicalNoteResponse> listNotes(@PathParam("encounterId") UUID id){return clinicalService.listNotes(id);}
    @POST @Path("/{encounterId}/notes/{noteId}/sign") @RequiresRoles({"DOCTOR","NURSE"})
    public ClinicalNoteResponse signNote(@PathParam("encounterId") UUID encounterId,@PathParam("noteId") UUID noteId){return clinicalService.signNote(encounterId,noteId);}
    @POST @Path("/{encounterId}/notes/{noteId}/amendments") @RequiresRoles({"DOCTOR"})
    public Response amendNote(@PathParam("encounterId") UUID encounterId,@PathParam("noteId") UUID noteId,@Valid ClinicalNoteCreateRequest request){return Response.status(201).entity(clinicalService.createAmendment(encounterId,noteId,request)).build();}
    @POST @Path("/{encounterId}/diagnoses") @RequiresRoles({"DOCTOR"})
    public Response addDiagnosis(@PathParam("encounterId") UUID id,@Valid DiagnosisCreateRequest request){return Response.status(201).entity(clinicalService.addDiagnosis(id,request)).build();}
    @GET @Path("/{encounterId}/diagnoses") @RequiresRoles({"DOCTOR","NURSE"})
    public java.util.List<EncounterDiagnosisResponse> listDiagnoses(@PathParam("encounterId") UUID id){return clinicalService.listDiagnoses(id);}
    @PATCH @Path("/{encounterId}/diagnoses/{encounterDiagnosisId}") @RequiresRoles({"DOCTOR"})
    public EncounterDiagnosisResponse updateDiagnosisStatus(@PathParam("encounterId") UUID id,@PathParam("encounterDiagnosisId") UUID diagnosisId,@Valid DiagnosisStatusUpdateRequest request){return clinicalService.updateDiagnosisStatus(id,diagnosisId,request);}
    @POST @Path("/{encounterId}/complete") @RequiresRoles({"DOCTOR"})
    public EncounterResponse complete(@PathParam("encounterId") UUID id){return clinicalService.completeEncounter(id);}

    @POST
    @Path("/{encounterId}/care-team")
    @RequiresRoles({"DOCTOR", "HOSPITAL_MANAGER"})
    public Response assignCareTeamMember(
            @PathParam("encounterId") UUID encounterId,
            @Valid CareTeamAssignRequest request) {
        return Response.status(201)
                .entity(clinicalService.assignCareTeamMember(encounterId, request))
                .build();
    }

    @GET
    @Path("/{encounterId}/care-team")
    @RequiresRoles({"DOCTOR", "NURSE"})
    public java.util.List<CareTeamResponse> listCareTeam(@PathParam("encounterId") UUID encounterId) {
        return clinicalService.listCareTeam(encounterId);
    }

    @POST
    @Path("/{encounterId}/care-team/{assignmentId}/release")
    @RequiresRoles({"DOCTOR", "HOSPITAL_MANAGER"})
    public CareTeamResponse releaseCareTeamMember(
            @PathParam("encounterId") UUID encounterId,
            @PathParam("assignmentId") UUID assignmentId) {
        return clinicalService.releaseCareTeamMember(encounterId, assignmentId);
    }
}

