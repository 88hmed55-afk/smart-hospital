package com.smarthospital.billing.rest;
import com.smarthospital.billing.dto.*;
import com.smarthospital.billing.service.BillingService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.UUID;
@Path("/billing/invoices") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class BillingResource { @EJB private BillingService service;
 @POST @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"}) public Response create(@Valid InvoiceCreateRequest r,@Context UriInfo uri){var x=service.createDraft(r);return Response.created(uri.getAbsolutePathBuilder().path(x.id.toString()).build()).entity(x).build();}
 @GET @Path("/{id}") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"}) public InvoiceResponse get(@PathParam("id") UUID id){return service.find(id);}
 @GET @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"}) public java.util.List<InvoiceResponse> list(@QueryParam("branchId") UUID branch,@QueryParam("status") String status,@DefaultValue("50") @QueryParam("limit") int limit){if(branch==null)throw new BadRequestException("branchId is required");return service.list(branch,status,limit);}

 @GET @Path("/services") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"})
 public java.util.List<ServiceCatalogResponse> services(@QueryParam("branchId") UUID branch,@QueryParam("q") String query,@DefaultValue("50") @QueryParam("limit") int limit){if(branch==null)throw new BadRequestException("branchId is required");return service.listActiveServices(branch,query,limit);}
 @GET @Path("/eligible-encounters") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"})
 public java.util.List<BillableEncounterResponse> eligibleEncounters(@QueryParam("branchId") UUID branch,@QueryParam("q") String query,@DefaultValue("20") @QueryParam("limit") int limit){if(branch==null)throw new BadRequestException("branchId is required");return service.listBillableEncounters(branch,query,limit);}

 @POST @Path("/{id}/issue") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"}) public InvoiceResponse issue(@PathParam("id") UUID id){return service.issue(id);}
 @POST @Path("/{id}/payments") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","ACCOUNTANT"}) public Response pay(@PathParam("id") UUID id,@Valid PaymentCreateRequest r){return Response.status(201).entity(service.recordPayment(id,r)).build();}
}
