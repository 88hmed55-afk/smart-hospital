package com.smarthospital.pharmacy.rest;

import com.smarthospital.pharmacy.dto.DispenseResponse;
import com.smarthospital.pharmacy.service.PharmacyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/pharmacy/dispenses")
@Produces(MediaType.APPLICATION_JSON)
public class PharmacyDispenseResource {
    @EJB
    private PharmacyService pharmacyService;

    @GET
    @Path("/{dispenseId}")
    @RequiresRoles({"PHARMACIST"})
    public DispenseResponse get(@PathParam("dispenseId") UUID dispenseId) {
        return pharmacyService.getDispense(dispenseId);
    }
}
