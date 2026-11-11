package com.smarthospital.organization.service;

import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.dto.BranchResponse;
import com.smarthospital.organization.dto.DepartmentResponse;
import com.smarthospital.organization.dto.DoctorOptionResponse;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.Department;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

@Stateless
public class OrganizationQueryService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService currentUserService;
    public List<BranchResponse> listActiveBranches() {
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        return em.createQuery("select b from HospitalBranch b where b.active = true and b.id in :branchIds order by b.name", HospitalBranch.class)
                .setParameter("branchIds", currentUser.branchIds())
                .getResultList().stream().map(BranchResponse::from).toList();
    }
    public List<DepartmentResponse> listActiveDepartments(UUID branchId) {
        currentUserService.requireBranchAccess(branchId);
        HospitalBranch branch = em.find(HospitalBranch.class, branchId);
        if (branch == null) throw BusinessException.notFound("Hospital branch");
        return em.createQuery("select d from Department d where d.branch.id = :branchId and d.active = true order by d.name", Department.class)
                .setParameter("branchId", branchId)
                .getResultList().stream().map(DepartmentResponse::from).toList();
    }
    public List<DoctorOptionResponse> listActiveDoctors(UUID branchId, UUID departmentId) {
        currentUserService.requireBranchAccess(branchId);
        String departmentFilter = departmentId == null ? "" : "and d.id = :departmentId ";
        String jpql = "select dp from DoctorProfile dp join fetch dp.staff sm "
                + "left join fetch sm.department d "
                + "where dp.active = true and sm.employmentStatus = 'ACTIVE' "
                + "and sm.branch.id = :branchId " + departmentFilter
                + "order by sm.firstName, sm.lastName";
        var query = em.createQuery(jpql, DoctorProfile.class).setParameter("branchId", branchId);
        if (departmentId != null) query.setParameter("departmentId", departmentId);
        return query.getResultList().stream().map(DoctorOptionResponse::from).toList();
    }

}
