package com.smarthospital.laboratory.rest;

import com.smarthospital.laboratory.dto.LabTestResponse;
import com.smarthospital.laboratory.service.LaboratoryService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/lab-tests")
@Produces(MediaType.APPLICATION_JSON)
public class LabTestCatalogResource {
    @EJB
    private LaboratoryService laboratoryService;

    @GET
    @RequiresRoles({"DOCTOR"})
    public List<LabTestResponse> search(
            @QueryParam("q") String query,
            @QueryParam("limit") Integer limit) {
        return laboratoryService.searchTests(query, limit == null ? 20 : limit);
    }
}
