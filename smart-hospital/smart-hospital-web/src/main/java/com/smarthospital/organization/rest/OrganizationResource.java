package com.smarthospital.organization.rest;

import com.smarthospital.organization.dto.BranchResponse;
import com.smarthospital.organization.dto.DepartmentResponse;
import com.smarthospital.organization.service.OrganizationQueryService;
import com.smarthospital.security.auth.RequiresRoles;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/organization")
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationResource {
    @EJB
    private OrganizationQueryService service;

    @GET
    @Path("/branches")
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE", "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER", "HOSPITAL_MANAGER"})
    public List<BranchResponse> branches() {
        return service.listActiveBranches();
    }

    @GET
    @Path("/branches/{branchId}/departments")
    @RequiresRoles({"HOSPITAL_ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE", "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER", "HOSPITAL_MANAGER"})
    public List<DepartmentResponse> departments(@PathParam("branchId") UUID branchId) {
        return service.listActiveDepartments(branchId);
    }
    @GET
    @Path("/branches/{branchId}/doctors")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST"})
    public List<com.smarthospital.organization.dto.DoctorOptionResponse> doctors(
            @PathParam("branchId") UUID branchId,
            @QueryParam("departmentId") UUID departmentId) {
        return service.listActiveDoctors(branchId, departmentId);
    }

}
