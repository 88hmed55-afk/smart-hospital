package com.smarthospital.pharmacy.rest;

import com.smarthospital.pharmacy.dto.PharmacyLocationResponse;
import com.smarthospital.pharmacy.service.PharmacyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import java.util.UUID;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/pharmacy/locations")
@Produces(MediaType.APPLICATION_JSON)
public class PharmacyLocationResource {
    @EJB
    private PharmacyService pharmacyService;

    @GET
    @RequiresRoles({"PHARMACIST"})
    public List<PharmacyLocationResponse> list(@QueryParam("branchId") UUID branchId) {
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required");
        return pharmacyService.listPharmacyLocations(branchId);
    }
}
