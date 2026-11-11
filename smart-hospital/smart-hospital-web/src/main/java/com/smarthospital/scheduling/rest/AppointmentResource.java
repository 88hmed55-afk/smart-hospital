package com.smarthospital.scheduling.rest;

import com.smarthospital.scheduling.dto.AppointmentCreateRequest;
import com.smarthospital.scheduling.dto.AppointmentResponse;
import com.smarthospital.scheduling.dto.AppointmentListItemResponse;
import com.smarthospital.common.api.PageResponse;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import com.smarthospital.scheduling.service.AppointmentService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.UUID;

@Path("/appointments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AppointmentResource {
    @EJB
    private AppointmentService appointmentService;

    @GET
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR"})
    public PageResponse<AppointmentListItemResponse> search(
            @QueryParam("branchId") UUID branchId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @jakarta.ws.rs.DefaultValue("0") @QueryParam("offset") int offset,
            @jakarta.ws.rs.DefaultValue("50") @QueryParam("limit") int limit) {
        final Instant fromInstant;
        final Instant toInstant;
        try {
            fromInstant = Instant.parse(from);
            toInstant = Instant.parse(to);
        } catch (RuntimeException exception) {
            throw new BadRequestException("from and to must be ISO-8601 timestamps", exception);
        }
        return appointmentService.search(branchId, fromInstant, toInstant, offset, limit);
    }

    @GET
    @Path("/{appointmentId}")
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE"})
    public AppointmentResponse find(@PathParam("appointmentId") UUID appointmentId) {
        return appointmentService.findById(appointmentId);
    }

    @POST
    @Path("/{appointmentId}/check-in")
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST"})
    public AppointmentResponse checkIn(@PathParam("appointmentId") UUID appointmentId) {
        return appointmentService.checkIn(appointmentId);
    }

    @POST
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST"})
    public Response book(@Valid AppointmentCreateRequest request, @Context UriInfo uriInfo) {
        AppointmentResponse created = appointmentService.book(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build())
                .entity(created).build();
    }
}
