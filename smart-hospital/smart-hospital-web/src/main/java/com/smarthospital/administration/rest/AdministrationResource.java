package com.smarthospital.administration.rest;

import com.smarthospital.administration.dto.AccountAdminResponse;
import com.smarthospital.administration.dto.AccountProvisionRequest;
import com.smarthospital.administration.dto.AccountStatusRequest;
import com.smarthospital.administration.dto.MfaResetRequest;
import com.smarthospital.administration.dto.RoleResponse;
import com.smarthospital.administration.dto.StaffCreateRequest;
import com.smarthospital.administration.dto.StaffResponse;
import com.smarthospital.administration.dto.StaffUpdateRequest;
import com.smarthospital.administration.service.AdministrationService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.List;
import java.util.UUID;

/** Administrative HTTP contract. GlassFish File Realm credentials are provisioned separately. */
@Path("/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiresRoles("HOSPITAL_ADMIN")
public class AdministrationResource {
    @EJB
    private AdministrationService service;

    @GET
    @Path("/staff")
    public List<StaffResponse> staff(
            @QueryParam("branchId") UUID branchId,
            @QueryParam("q") String search,
            @QueryParam("status") String status,
            @DefaultValue("100") @QueryParam("limit") @Min(1) @Max(200) int limit) {
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required.");
        return service.listStaff(branchId, search, status, limit);
    }

    @POST
    @Path("/staff")
    public Response createStaff(@Valid StaffCreateRequest request, @Context UriInfo uriInfo) {
        StaffResponse created = service.createStaff(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created).build();
    }

    @PUT
    @Path("/staff/{staffId}")
    public StaffResponse updateStaff(@PathParam("staffId") UUID staffId, @Valid StaffUpdateRequest request) {
        return service.updateStaff(staffId, request);
    }

    @GET
    @Path("/roles")
    public List<RoleResponse> roles() {
        return service.listRoles();
    }

    @GET
    @Path("/accounts")
    public List<AccountAdminResponse> accounts(
            @QueryParam("branchId") UUID branchId,
            @DefaultValue("100") @QueryParam("limit") @Min(1) @Max(200) int limit) {
        if (branchId == null) throw new jakarta.ws.rs.BadRequestException("branchId is required.");
        return service.listAccounts(branchId, limit);
    }

    @POST
    @Path("/accounts")
    public Response provisionAccount(@Valid AccountProvisionRequest request, @Context UriInfo uriInfo) {
        AccountAdminResponse created = service.provisionAccount(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created).build();
    }

    @PATCH
    @Path("/accounts/{accountId}/status")
    public AccountAdminResponse updateAccountStatus(
            @PathParam("accountId") UUID accountId,
            @Valid AccountStatusRequest request) {
        return service.updateAccountStatus(accountId, request);
    }

    /** Clears a locked-out second factor after out-of-band identity verification. */
    @PATCH
    @Path("/accounts/{accountId}/mfa-reset")
    public Response resetMfa(@PathParam("accountId") UUID accountId, MfaResetRequest request) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("branchId and a verification reason are required.");
        }
        service.resetMfa(accountId, request.branchId, request.reason);
        return Response.noContent().build();
    }
}
