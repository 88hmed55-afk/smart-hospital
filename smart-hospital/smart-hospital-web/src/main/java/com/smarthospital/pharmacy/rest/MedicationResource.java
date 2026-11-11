package com.smarthospital.pharmacy.rest;

import com.smarthospital.pharmacy.dto.MedicationResponse;
import com.smarthospital.pharmacy.service.PharmacyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/medications")
@Produces(MediaType.APPLICATION_JSON)
public class MedicationResource {
    @EJB
    private PharmacyService pharmacyService;

    @GET
    @RequiresRoles({"DOCTOR", "PHARMACIST", "INVENTORY_OFFICER", "HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public List<MedicationResponse> search(
            @QueryParam("q") String query,
            @QueryParam("limit") Integer limit) {
        return pharmacyService.searchMedications(query, limit == null ? 20 : limit);
    }
}
