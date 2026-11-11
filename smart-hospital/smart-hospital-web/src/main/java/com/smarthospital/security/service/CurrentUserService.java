package com.smarthospital.security.service;

import com.smarthospital.common.error.BusinessException;
import com.smarthospital.common.persistence.DbValues;
import com.smarthospital.security.auth.AuthenticatedUserHolder;
import com.smarthospital.security.auth.SecuritySettings;
import com.smarthospital.security.domain.CurrentUser;
import jakarta.annotation.Resource;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the authenticated identity to an active hospital account, its roles and
 * its branch memberships.
 *
 * <p>Production authentication is performed by {@code AuthContextFilter} using an
 * opaque bearer session; the resolved identity is carried in a request-scoped
 * holder. A legacy container-principal path is retained so the application still
 * works if BASIC authentication is re-enabled for a controlled pilot.</p>
 */
@Stateless
public class CurrentUserService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @Resource
    private SessionContext sessionContext;

    @Inject
    private AuthenticatedUserHolder authenticatedUser;

    public CurrentUser requireCurrentUser() {
        CurrentUser cached = authenticatedUser == null ? null : authenticatedUser.get();
        if (cached != null) {
            return cached;
        }
        if (sessionContext == null || sessionContext.getCallerPrincipal() == null) {
            throw BusinessException.unauthorized("AUTHENTICATION_REQUIRED", "An authenticated hospital account is required.");
        }
        String username = sessionContext.getCallerPrincipal().getName();
        if (username == null || username.isBlank()) {
            throw BusinessException.unauthorized("AUTHENTICATION_REQUIRED", "An authenticated hospital account is required.");
        }
        return resolve(username, true);
    }

    /** Resolves an account for the bearer-token filter without container role checks. */
    public CurrentUser resolveActiveUser(String username) {
        return resolve(username, false);
    }

    private CurrentUser resolve(String username, boolean enforceContainerGroups) {
        @SuppressWarnings("unchecked")
        List<Object[]> accountRows = em.createNativeQuery("""
                SELECT ua.id, ua.staff_id, ua.account_status, sm.employment_status,
                       ua.must_change_password, ua.mfa_enrolled_at
                  FROM hospital.user_accounts ua
                  LEFT JOIN hospital.staff_members sm ON sm.id = ua.staff_id
                 WHERE lower(ua.username) = lower(?1)
                """)
                .setParameter(1, username)
                .getResultList();

        if (accountRows.isEmpty()) {
            throw BusinessException.forbidden("HOSPITAL_ACCOUNT_NOT_MAPPED", "The authenticated account is not mapped to an active hospital user.");
        }
        Object[] row = accountRows.get(0);
        UUID userId = DbValues.uuid(row[0]);
        UUID staffId = DbValues.uuid(row[1]);
        String status = (String) row[2];
        String employmentStatus = (String) row[3];
        if (!"ACTIVE".equals(status)) {
            throw BusinessException.forbidden("HOSPITAL_ACCOUNT_INACTIVE", "The hospital account is not active.");
        }
        if (staffId != null && !"ACTIVE".equals(employmentStatus)) {
            throw BusinessException.forbidden("STAFF_ACCOUNT_INACTIVE", "The linked staff profile is not active.");
        }

        @SuppressWarnings("unchecked")
        List<String> roleCodes = em.createNativeQuery("""
                SELECT r.code
                  FROM hospital.user_roles ur
                  JOIN hospital.roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ?1
                 ORDER BY r.code
                """)
                .setParameter(1, userId)
                .getResultList();

        if (enforceContainerGroups) {
            @SuppressWarnings("unchecked")
            List<String> allRoleCodes = em.createNativeQuery("SELECT code FROM hospital.roles ORDER BY code")
                    .getResultList();
            boolean missingContainerGroup = roleCodes.stream().anyMatch(code -> !sessionContext.isCallerInRole(code));
            boolean unexpectedContainerGroup = allRoleCodes.stream()
                    .anyMatch(code -> !roleCodes.contains(code) && sessionContext.isCallerInRole(code));
            if (roleCodes.isEmpty() || missingContainerGroup || unexpectedContainerGroup) {
                throw BusinessException.forbidden("ROLE_MAPPING_MISMATCH", "Container role groups must exactly match the hospital account role assignments.");
            }
        }

        if (roleCodes.isEmpty()) {
            throw BusinessException.forbidden("NO_ROLE_ASSIGNED", "The account has no assigned hospital role.");
        }

        @SuppressWarnings("unchecked")
        List<?> branchIdsRaw = em.createNativeQuery("""
                SELECT uba.branch_id
                  FROM hospital.user_branch_access uba
                  JOIN hospital.hospital_branches hb ON hb.id = uba.branch_id
                 WHERE uba.user_id = ?1
                   AND hb.is_active = TRUE
                 ORDER BY uba.is_default DESC, uba.branch_id
                """)
                .setParameter(1, userId)
                .getResultList();
        List<UUID> branchIds = branchIdsRaw.stream().map(DbValues::uuid).toList();
        if (branchIds.isEmpty()) {
            throw BusinessException.forbidden("NO_BRANCH_ACCESS", "The account has no assigned hospital branch.");
        }

        @SuppressWarnings("unchecked")
        List<?> defaultRows = em.createNativeQuery("""
                SELECT uba.branch_id
                  FROM hospital.user_branch_access uba
                  JOIN hospital.hospital_branches hb ON hb.id = uba.branch_id
                 WHERE uba.user_id = ?1
                   AND uba.is_default = TRUE
                   AND hb.is_active = TRUE
                 LIMIT 1
                """)
                .setParameter(1, userId)
                .getResultList();
        UUID defaultBranchId = defaultRows.isEmpty() ? branchIds.get(0) : DbValues.uuid(defaultRows.get(0));
        boolean mustChangePassword = Boolean.TRUE.equals(row[4]) || Integer.valueOf(1).equals(row[4]);
        boolean mfaEnabled = row[5] != null;
        boolean mfaEnrollmentRequired = !mfaEnabled && SecuritySettings.mfaRequiredFor(roleCodes);

        return new CurrentUser(userId, staffId, username, roleCodes, branchIds, defaultBranchId,
                mustChangePassword, mfaEnabled, mfaEnrollmentRequired);
    }

    public UUID requireDefaultBranchId() {
        return requireCurrentUser().defaultBranchId();
    }

    public UUID requireStaffId() {
        UUID staffId = requireCurrentUser().staffId();
        if (staffId == null) {
            throw BusinessException.forbidden("STAFF_PROFILE_REQUIRED", "This operation requires an account linked to a staff profile.");
        }
        return staffId;
    }

    public UUID requireDoctorStaffId() {
        UUID staffId = requireStaffId();
        Long count = ((Number) em.createNativeQuery("""
                SELECT count(*)
                  FROM hospital.doctor_profiles dp
                  JOIN hospital.staff_members sm ON sm.id = dp.staff_id
                 WHERE dp.staff_id = ?1
                   AND dp.is_active = TRUE
                   AND sm.employment_status = 'ACTIVE'
                """)
                .setParameter(1, staffId)
                .getSingleResult()).longValue();
        if (count == 0) {
            throw BusinessException.forbidden("ACTIVE_DOCTOR_PROFILE_REQUIRED", "This operation requires an active doctor profile.");
        }
        return staffId;
    }

    public void requireBranchAccess(UUID branchId) {
        if (branchId == null || !requireCurrentUser().branchIds().contains(branchId)) {
            throw BusinessException.forbidden("BRANCH_ACCESS_DENIED", "You do not have access to the requested hospital branch.");
        }
    }

    public void requirePatientAccess(UUID patientId) {
        CurrentUser user = requireCurrentUser();
        Long count = ((Number) em.createNativeQuery("""
                SELECT count(*)
                  FROM hospital.patient_branch_access pba
                  JOIN hospital.user_branch_access uba ON uba.branch_id = pba.branch_id
                  JOIN hospital.hospital_branches hb ON hb.id = pba.branch_id AND hb.is_active = TRUE
                 WHERE pba.patient_id = ?1
                   AND uba.user_id = ?2
                   AND pba.revoked_at IS NULL
                """)
                .setParameter(1, patientId)
                .setParameter(2, user.userId())
                .getSingleResult()).longValue();
        if (count == 0) {
            throw BusinessException.forbidden("PATIENT_ACCESS_DENIED", "You do not have authorized access to this patient record.");
        }
    }

    public void requirePatientAccessInBranch(UUID patientId, UUID branchId) {
        CurrentUser user = requireCurrentUser();
        if (!user.branchIds().contains(branchId)) {
            throw BusinessException.forbidden("BRANCH_ACCESS_DENIED", "You do not have access to the requested hospital branch.");
        }
        Long count = ((Number) em.createNativeQuery("""
                SELECT count(*)
                  FROM hospital.patient_branch_access pba
                 WHERE pba.patient_id = ?1
                   AND pba.branch_id = ?2
                   AND pba.revoked_at IS NULL
                """)
                .setParameter(1, patientId)
                .setParameter(2, branchId)
                .getSingleResult()).longValue();
        if (count == 0) {
            throw BusinessException.forbidden("PATIENT_BRANCH_ACCESS_DENIED", "The patient is not registered for access in this branch.");
        }
    }
}
