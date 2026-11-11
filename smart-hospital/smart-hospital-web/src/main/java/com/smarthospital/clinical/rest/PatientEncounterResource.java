package com.smarthospital.clinical.rest;

import com.smarthospital.clinical.dto.EncounterResponse;
import com.smarthospital.clinical.service.ClinicalService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/patients/{patientId}/encounters")
@Produces(MediaType.APPLICATION_JSON)
public class PatientEncounterResource {
    @EJB private ClinicalService clinicalService;

    @GET
    @RequiresRoles({"DOCTOR", "NURSE"})
    public List<EncounterResponse> list(@PathParam("patientId") UUID patientId, @QueryParam("limit") Integer limit) {
        return clinicalService.listPatientEncounters(patientId, limit == null ? 20 : limit);
    }
}
