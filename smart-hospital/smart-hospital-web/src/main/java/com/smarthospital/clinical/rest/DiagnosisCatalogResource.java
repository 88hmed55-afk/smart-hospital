package com.smarthospital.clinical.rest;

import com.smarthospital.clinical.dto.DiagnosisCatalogResponse;
import com.smarthospital.clinical.service.ClinicalService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/diagnoses")
@Produces(MediaType.APPLICATION_JSON)
public class DiagnosisCatalogResource {
    @EJB private ClinicalService clinicalService;
    @GET @RequiresRoles({"DOCTOR","NURSE"})
    public List<DiagnosisCatalogResponse> search(@QueryParam("q") String query,@QueryParam("limit") Integer limit) {
        return clinicalService.searchDiagnosisCatalog(query,limit==null?20:limit);
    }
}
