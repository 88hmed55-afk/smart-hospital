package com.smarthospital.admission.rest;
import com.smarthospital.admission.dto.*;
import com.smarthospital.admission.service.AdmissionService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.UUID;
@Path("/admissions") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class AdmissionResource { @EJB private AdmissionService service;
 @POST @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","DOCTOR","RECEPTIONIST"}) public Response admit(@Valid AdmissionCreateRequest r,@Context UriInfo uri){var x=service.admit(r);return Response.created(uri.getAbsolutePathBuilder().path(x.id.toString()).build()).entity(x).build();}
 @GET @Path("/active") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","DOCTOR","NURSE"}) public java.util.List<AdmissionResponse> active(@QueryParam("branchId") UUID branch,@DefaultValue("50") @QueryParam("limit") int limit){return service.listActive(branch,limit);}
 @GET @Path("/beds") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","DOCTOR","NURSE","RECEPTIONIST"}) public java.util.List<BedResponse> beds(@QueryParam("branchId") UUID branch,@QueryParam("wardId") UUID ward,@DefaultValue("false") @QueryParam("availableOnly") boolean available){if(branch==null)throw new BadRequestException("branchId is required");return service.listBeds(branch,ward,available);}
 @POST @Path("/{id}/bed-assignments") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","NURSE","DOCTOR"}) public AdmissionResponse assign(@PathParam("id") UUID id,@Valid BedAssignRequest r){return service.assignBed(id,r);}
 @POST @Path("/{id}/bed-transfers") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","NURSE","DOCTOR"}) public AdmissionResponse transfer(@PathParam("id") UUID id,@Valid BedAssignRequest r){return service.transferBed(id,r);}
 @POST @Path("/{id}/discharge") @RequiresRoles({"HOSPITAL_ADMIN","HOSPITAL_MANAGER","DOCTOR"}) public AdmissionResponse discharge(@PathParam("id") UUID id,@Valid DischargeRequest r){return service.discharge(id,r);}
}
