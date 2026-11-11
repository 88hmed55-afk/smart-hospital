package com.smarthospital.procurement.rest;

import com.smarthospital.procurement.dto.*;
import com.smarthospital.procurement.service.ProcurementService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import com.smarthospital.pharmacy.dto.PharmacyLocationResponse;
import java.util.UUID;

@Path("/procurement")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProcurementResource {
    @EJB private ProcurementService service;

    @GET
    @Path("/suppliers")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public List<SupplierResponse> suppliers(@QueryParam("q") String query,
                                            @DefaultValue("50") @QueryParam("limit") int limit) {
        return service.listSuppliers(query, limit);
    }

    @POST
    @Path("/suppliers")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public Response createSupplier(@Valid SupplierCreateRequest request, @Context UriInfo uri) {
        SupplierResponse created = service.createSupplier(request);
        return Response.created(uri.getAbsolutePathBuilder().path(created.id.toString()).build()).entity(created).build();
    }

    @GET
    @Path("/locations")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public List<PharmacyLocationResponse> locations(@QueryParam("branchId") UUID branchId) {
        if (branchId == null) throw new BadRequestException("branchId is required");
        return service.listInventoryLocations(branchId);
    }

    @GET
    @Path("/purchase-orders")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public List<PurchaseOrderResponse> orders(@QueryParam("branchId") UUID branchId,
                                              @QueryParam("status") String status,
                                              @DefaultValue("50") @QueryParam("limit") int limit) {
        if (branchId == null) throw new BadRequestException("branchId is required");
        return service.listOrders(branchId, status, limit);
    }

    @POST
    @Path("/purchase-orders")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public Response createOrder(@Valid PurchaseOrderCreateRequest request, @Context UriInfo uri) {
        PurchaseOrderResponse created = service.createDraft(request);
        return Response.created(uri.getAbsolutePathBuilder().path(created.id.toString()).build()).entity(created).build();
    }

    @POST
    @Path("/purchase-orders/{orderId}/submit")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public PurchaseOrderResponse submit(@PathParam("orderId") UUID orderId) { return service.submit(orderId); }

    @POST
    @Path("/purchase-orders/{orderId}/approval")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER"})
    public PurchaseOrderResponse approve(@PathParam("orderId") UUID orderId, @Valid PurchaseOrderApprovalRequest request) {
        if (request == null || request.approved == null) throw new BadRequestException("approved is required");
        return service.approve(orderId, request.approved, request.note);
    }

    @POST
    @Path("/purchase-orders/{orderId}/order")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public PurchaseOrderResponse markOrdered(@PathParam("orderId") UUID orderId) { return service.markOrdered(orderId); }

    @POST
    @Path("/purchase-orders/{orderId}/receipts")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER"})
    public Response receive(@PathParam("orderId") UUID orderId, @Valid GoodsReceiptCreateRequest request,
                            @Context UriInfo uri) {
        GoodsReceiptResponse created = service.receive(orderId, request);
        return Response.created(uri.getAbsolutePathBuilder().path(created.id.toString()).build()).entity(created).build();
    }
}
