package com.smarthospital.accounting.rest;

import com.smarthospital.accounting.dto.AccountResponse;
import com.smarthospital.accounting.dto.JournalEntryResponse;
import com.smarthospital.accounting.dto.ManualJournalCreateRequest;
import com.smarthospital.accounting.dto.TrialBalanceResponse;
import com.smarthospital.accounting.service.AccountingService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

@Path("/accounting")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AccountingResource {
    @EJB private AccountingService service;

    @GET @Path("/accounts")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public List<AccountResponse> accounts(@QueryParam("branchId") UUID branchId) {
        if (branchId == null) throw new BadRequestException("branchId is required");
        return service.listAccounts(branchId);
    }

    @GET @Path("/journals")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public List<JournalEntryResponse> journals(@QueryParam("branchId") UUID branchId,
                                               @QueryParam("fromDate") String fromDateValue,
                                               @QueryParam("toDate") String toDateValue,
                                               @DefaultValue("50") @QueryParam("limit") int limit) {
        if (branchId == null) throw new BadRequestException("branchId is required");
        return service.listEntries(branchId, parseDate(fromDateValue, "fromDate"), parseDate(toDateValue, "toDate"), limit);
    }

    @GET @Path("/trial-balance")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public TrialBalanceResponse trialBalance(@QueryParam("branchId") UUID branchId,
                                             @QueryParam("asOf") String asOfValue) {
        if (branchId == null) throw new BadRequestException("branchId is required");
        return service.trialBalance(branchId, parseDate(asOfValue, "asOf"));
    }

    private java.time.LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return java.time.LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException exception) { throw new BadRequestException(field + " must use YYYY-MM-DD format."); }
    }

    @GET @Path("/journals/{id}")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public JournalEntryResponse getJournal(@PathParam("id") UUID id) { return service.findEntry(id); }

    @POST @Path("/journals")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public Response createDraft(@Valid ManualJournalCreateRequest request, @Context UriInfo uriInfo) {
        JournalEntryResponse response = service.createManualDraft(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(response.id.toString()).build()).entity(response).build();
    }

    @POST @Path("/journals/{id}/post")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public JournalEntryResponse post(@PathParam("id") UUID id) { return service.postEntry(id); }

    @POST @Path("/journals/{id}/reversal")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "ACCOUNTANT"})
    public Response reverse(@PathParam("id") UUID id) { return Response.status(Response.Status.CREATED).entity(service.createReversalDraft(id)).build(); }
}
