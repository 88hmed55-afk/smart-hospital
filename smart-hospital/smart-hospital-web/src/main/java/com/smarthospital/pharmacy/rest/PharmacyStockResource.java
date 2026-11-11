package com.smarthospital.pharmacy.rest;

import com.smarthospital.pharmacy.dto.StockAvailabilityResponse;
import com.smarthospital.pharmacy.service.PharmacyService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/pharmacy/stock")
@Produces(MediaType.APPLICATION_JSON)
public class PharmacyStockResource {
    @EJB
    private PharmacyService pharmacyService;

    @GET
    @RequiresRoles({"PHARMACIST"})
    public List<StockAvailabilityResponse> list(
            @QueryParam("branchId") UUID branchId,
            @QueryParam("q") String query,
            @QueryParam("locationId") UUID locationId,
            @QueryParam("limit") Integer limit) {
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required");
        return pharmacyService.listStockAvailability(branchId, query, locationId, limit == null ? 50 : limit);
    }
}
