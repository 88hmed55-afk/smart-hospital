package com.smarthospital.administration.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.administration.dto.AccountAdminResponse;
import com.smarthospital.administration.dto.AccountProvisionRequest;
import com.smarthospital.administration.dto.AccountStatusRequest;
import com.smarthospital.administration.dto.RoleResponse;
import com.smarthospital.administration.dto.StaffCreateRequest;
import com.smarthospital.administration.dto.StaffResponse;
import com.smarthospital.administration.dto.StaffUpdateRequest;
import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.security.auth.PasswordHasher;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Administrative staff directory and application-managed accounts (PBKDF2 passwords, roles, branches). */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AdministrationService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;
    @EJB private CurrentUserService users;
    @EJB private AuditService audit;
    public List<StaffResponse> listStaff(UUID branchId, String search, String status, int limit) {
        requireAdmin(); users.requireBranchAccess(branchId);
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        String normalized = search == null ? "" : search.trim().replace("%", "").replace("_", "");
        if (normalized.length() > 80) throw BusinessException.badRequest("STAFF_SEARCH_TOO_LONG", "Staff search is limited to 80 characters.");
        String pattern = normalized.isBlank() ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        String statusFilter = normalizeStatusFilter(status);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT s.id, s.branch_id, b.name, s.department_id, d.name,
                       s.employee_number, s.first_name, s.last_name, s.work_email,
                       s.phone_number, s.job_title, s.employment_status, s.hired_on,
                       ua.username
                  FROM hospital.staff_members s
                  JOIN hospital.hospital_branches b ON b.id = s.branch_id
                  LEFT JOIN hospital.departments d ON d.id = s.department_id
                  LEFT JOIN hospital.user_accounts ua ON ua.staff_id = s.id
                 WHERE s.branch_id = ?1
                   AND (?2 IS NULL OR lower(s.employee_number) LIKE ?2
                        OR lower(s.first_name) LIKE ?2 OR lower(s.last_name) LIKE ?2
                        OR lower(coalesce(s.job_title,'')) LIKE ?2)
                   AND (?3 IS NULL OR s.employment_status = ?3)
                 ORDER BY s.last_name, s.first_name, s.employee_number
                """)
                .setParameter(1, branchId)
                .setParameter(2, pattern)
                .setParameter(3, statusFilter)
                .setMaxResults(safeLimit)
                .getResultList();
        return rows.stream().map(this::staffResponse).toList();
    }
    public StaffResponse createStaff(StaffCreateRequest request) {
        if (request == null || request.branchId == null || request.departmentId == null) {
            throw BusinessException.badRequest("STAFF_REQUEST_INVALID", "Branch and department are required.");
        }
        requireAdmin(); users.requireBranchAccess(request.branchId);
        String employeeNumber = cleanRequired(request.employeeNumber, "employeeNumber", 32);
        String firstName = cleanRequired(request.firstName, "firstName", 80);
        String lastName = cleanRequired(request.lastName, "lastName", 80);
        String email = cleanOptional(request.workEmail, 254);
        String phone = cleanOptional(request.phoneNumber, 32);
        String title = cleanOptional(request.jobTitle, 100);
        requireActiveDepartment(request.departmentId, request.branchId);
        Long duplicate = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.staff_members WHERE branch_id=?1 AND lower(employee_number)=lower(?2)")
                .setParameter(1, request.branchId).setParameter(2, employeeNumber).getSingleResult()).longValue();
        if (duplicate > 0) throw BusinessException.conflict("EMPLOYEE_NUMBER_EXISTS", "This employee number already exists in the selected branch.");

        UUID id = UUID.randomUUID();
        try {
            em.createNativeQuery("""
                    INSERT INTO hospital.staff_members
                        (id, branch_id, department_id, employee_number, first_name, last_name,
                         work_email, phone_number, job_title, employment_status, hired_on)
                    VALUES (?1, ?2, ?3, ?4, ?5, ?6,
                            ?7, ?8, ?9, 'ACTIVE', ?10)
                    """)
                    .setParameter(1, id).setParameter(2, request.branchId)
                    .setParameter(3, request.departmentId).setParameter(4, employeeNumber)
                    .setParameter(5, firstName).setParameter(6, lastName)
                    .setParameter(7, email).setParameter(8, phone)
                    .setParameter(9, title).setParameter(10, request.hiredOn)
                    .executeUpdate();
        } catch (PersistenceException exception) {
            if (hasSqlState(exception, "23505")) throw BusinessException.conflict("EMPLOYEE_NUMBER_EXISTS", "This employee number already exists in the selected branch.");
            throw exception;
        }
        audit.recordAction("STAFF_CREATED", "STAFF_MEMBER", id, "staff record created", request.branchId);
        return findStaff(id, request.branchId);
    }
    public StaffResponse updateStaff(UUID staffId, StaffUpdateRequest request) {
        if (request == null) throw BusinessException.badRequest("STAFF_REQUEST_INVALID", "Staff update details are required.");
        CurrentUser actor = requireAdmin();
        Object[] existing = (Object[]) em.createNativeQuery("SELECT branch_id, department_id FROM hospital.staff_members WHERE id=?1 FOR UPDATE")
                .setParameter(1, staffId).getResultStream().findFirst().orElse(null);
        if (existing == null) throw BusinessException.notFound("Staff member");
        UUID branchId = DbValues.uuid(existing[0]);
        users.requireBranchAccess(branchId);
        if (!"ACTIVE".equalsIgnoreCase(request.employmentStatus == null ? "" : request.employmentStatus.trim())
                && staffId.equals(actor.staffId())) {
            throw BusinessException.conflict("CANNOT_DEACTIVATE_CURRENT_ADMIN_STAFF", "You cannot change your own linked staff profile to an inactive status.");
        }
        UUID departmentId = request.departmentId;
        if (departmentId != null) requireActiveDepartment(departmentId, branchId);
        String firstName = cleanRequired(request.firstName, "firstName", 80);
        String lastName = cleanRequired(request.lastName, "lastName", 80);
        String email = cleanOptional(request.workEmail, 254);
        String phone = cleanOptional(request.phoneNumber, 32);
        String title = cleanOptional(request.jobTitle, 100);
        String employmentStatus = request.employmentStatus == null ? "" : request.employmentStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ON_LEAVE", "SUSPENDED", "TERMINATED").contains(employmentStatus)) {
            throw BusinessException.badRequest("EMPLOYMENT_STATUS_INVALID", "Supported employment statuses are ACTIVE, ON_LEAVE, SUSPENDED and TERMINATED.");
        }
        em.createNativeQuery("""
                UPDATE hospital.staff_members
                   SET department_id=?1, first_name=?2, last_name=?3,
                       work_email=?4, phone_number=?5, job_title=?6,
                       employment_status=?7, updated_at=now()
                 WHERE id=?8
                """)
                .setParameter(1, departmentId).setParameter(2, firstName)
                .setParameter(3, lastName).setParameter(4, email)
                .setParameter(5, phone).setParameter(6, title)
                .setParameter(7, employmentStatus).setParameter(8, staffId)
                .executeUpdate();
        if (!"ACTIVE".equals(employmentStatus)) {
            @SuppressWarnings("unchecked")
            List<?> linkedBranchRows = em.createNativeQuery("""
                    SELECT uba.branch_id
                      FROM hospital.user_accounts ua
                      JOIN hospital.user_branch_access uba ON uba.user_id=ua.id
                     WHERE ua.staff_id=?1
                    """).setParameter(1, staffId).getResultList();
            for (Object branchValue : linkedBranchRows) {
                UUID linkedBranch = DbValues.uuid(branchValue);
                if (!actor.branchIds().contains(linkedBranch)) {
                    throw BusinessException.forbidden("STAFF_ACCOUNT_CROSS_BRANCH_SCOPE", "Changing this staff status would disable an account in a branch outside your administrative scope.");
                }
            }
            em.createNativeQuery("UPDATE hospital.user_accounts SET account_status='DISABLED', failed_login_count=0, locked_until=NULL, updated_at=now() WHERE staff_id=?1")
                    .setParameter(1, staffId).executeUpdate();
        }
        audit.recordAction("STAFF_UPDATED", "STAFF_MEMBER", staffId, "status=" + employmentStatus, branchId);
        return findStaff(staffId, branchId);
    }
    public List<RoleResponse> listRoles() {
        requireAdmin();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT code, name, description, is_system_role FROM hospital.roles ORDER BY code").getResultList();
        List<RoleResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            RoleResponse item = new RoleResponse(); item.code = (String) row[0]; item.name = (String) row[1];
            item.description = (String) row[2]; item.systemRole = Boolean.TRUE.equals(row[3]); result.add(item);
        }
        return result;
    }
    public List<AccountAdminResponse> listAccounts(UUID branchId, int limit) {
        CurrentUser actor = requireAdmin(); users.requireBranchAccess(branchId);
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        @SuppressWarnings("unchecked")
        List<Object[]> baseRows = em.createNativeQuery("""
                SELECT ua.id, ua.staff_id, concat_ws(' ', sm.first_name, sm.last_name) AS staff_name,
                       ua.username, ua.account_status, ua.must_change_password, ua.last_login_at, ua.created_at,
                       ua.mfa_enrolled_at
                  FROM hospital.user_accounts ua
                  LEFT JOIN hospital.staff_members sm ON sm.id = ua.staff_id
                 WHERE EXISTS (SELECT 1 FROM hospital.user_branch_access allowed
                                WHERE allowed.user_id=ua.id AND allowed.branch_id=?1)
                 ORDER BY ua.username
                """)
                .setParameter(1, branchId).setMaxResults(safeLimit).getResultList();
        Map<UUID, AccountAdminResponse> grouped = new LinkedHashMap<>();
        for (Object[] row : baseRows) {
            UUID id = DbValues.uuid(row[0]);
            AccountAdminResponse dto = new AccountAdminResponse(); dto.id = id; dto.staffId = DbValues.uuid(row[1]);
            dto.staffName = (String) row[2]; dto.username = (String) row[3]; dto.accountStatus = (String) row[4];
            dto.mustChangePassword = Boolean.TRUE.equals(row[5]); dto.lastLoginAt = instant(row[6]); dto.createdAt = instant(row[7]);
            dto.mfaEnabled = row[8] != null;
            grouped.put(id, dto);
        }
        if (grouped.isEmpty()) return List.of();
        @SuppressWarnings("unchecked")
        List<Object[]> roleRows = em.createNativeQuery("""
                SELECT ur.user_id, r.code FROM hospital.user_roles ur
                JOIN hospital.roles r ON r.id=ur.role_id
                WHERE ur.user_id IN (SELECT target.user_id FROM hospital.user_branch_access target WHERE target.branch_id=?1)
                ORDER BY ur.user_id, r.code
                """).setParameter(1, branchId).getResultList();
        for (Object[] row : roleRows) {
            AccountAdminResponse dto = grouped.get(DbValues.uuid(row[0]));
            if (dto != null && !dto.roleCodes.contains((String) row[1])) dto.roleCodes.add((String) row[1]);
        }
        // Expose only branch memberships that the current administrator is also authorized to see.
        @SuppressWarnings("unchecked")
        List<Object[]> branchRows = em.createNativeQuery("""
                SELECT target.user_id, target.branch_id, target.is_default
                  FROM hospital.user_branch_access target
                  JOIN hospital.user_branch_access visible
                    ON visible.user_id=?1 AND visible.branch_id=target.branch_id
                 WHERE target.user_id IN (SELECT scoped.user_id FROM hospital.user_branch_access scoped WHERE scoped.branch_id=?2)
                 ORDER BY target.user_id, target.branch_id
                """)
                .setParameter(1, actor.userId()).setParameter(2, branchId).getResultList();
        for (Object[] row : branchRows) {
            AccountAdminResponse dto = grouped.get(DbValues.uuid(row[0]));
            if (dto != null) {
                UUID visibleBranch = DbValues.uuid(row[1]); dto.branchIds.add(visibleBranch);
                if (Boolean.TRUE.equals(row[2])) dto.defaultBranchId = visibleBranch;
            }
        }
        return List.copyOf(grouped.values());
    }
    public AccountAdminResponse provisionAccount(AccountProvisionRequest request) {
        if (request == null || request.staffId == null || request.roleCodes == null || request.branchIds == null
                || request.defaultBranchId == null || request.username == null) {
            throw BusinessException.badRequest("ACCOUNT_PROVISION_INVALID", "Username, staff, roles, branch access and a default branch are required.");
        }
        if (!PasswordHasher.isStrong(request.initialPassword)) {
            throw BusinessException.badRequest("PASSWORD_POLICY_VIOLATION",
                    "The initial password must be 12-128 characters and include upper case, lower case, a digit and a symbol.");
        }
        CurrentUser actor = requireAdmin();
        String username = request.username.trim();
        if (!username.matches("[A-Za-z0-9._-]{3,80}")) throw BusinessException.badRequest("USERNAME_INVALID", "Username must contain 3–80 letters, digits, dots, underscores or hyphens.");
        Object[] staff = (Object[]) em.createNativeQuery("SELECT branch_id, employment_status FROM hospital.staff_members WHERE id=?1 FOR UPDATE")
                .setParameter(1, request.staffId).getResultStream().findFirst().orElse(null);
        if (staff == null) throw BusinessException.notFound("Staff member");
        UUID homeBranchId = DbValues.uuid(staff[0]);
        if (!"ACTIVE".equals(staff[1])) throw BusinessException.conflict("STAFF_NOT_ACTIVE", "Only active staff can receive a new system account.");
        users.requireBranchAccess(homeBranchId);
        Long linked = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.user_accounts WHERE staff_id=?1")
                .setParameter(1, request.staffId).getSingleResult()).longValue();
        if (linked > 0) throw BusinessException.conflict("STAFF_ALREADY_HAS_ACCOUNT", "This staff member already has a system account.");
        Long usernameCount = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.user_accounts WHERE lower(username)=lower(?1)")
                .setParameter(1, username).getSingleResult()).longValue();
        if (usernameCount > 0) throw BusinessException.conflict("USERNAME_ALREADY_EXISTS", "This username is already mapped in the hospital database.");

        List<UUID> branchIds = request.branchIds.stream().filter(id -> id != null).distinct().toList();
        if (branchIds.isEmpty() || !branchIds.contains(homeBranchId) || !branchIds.contains(request.defaultBranchId)) {
            throw BusinessException.badRequest("ACCOUNT_BRANCH_SCOPE_INVALID", "The staff home branch and the default branch must both be included in the account's branch access list.");
        }
        for (UUID branchId : branchIds) {
            users.requireBranchAccess(branchId);
            Long count = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.hospital_branches WHERE id=?1 AND is_active=TRUE")
                    .setParameter(1, branchId).getSingleResult()).longValue();
            if (count == 0) throw BusinessException.badRequest("ACCOUNT_BRANCH_INACTIVE", "Every assigned branch must exist and be active.");
        }
        Set<String> roleCodes = new HashSet<>();
        for (String raw : request.roleCodes) {
            if (raw == null || raw.isBlank()) continue;
            String code = raw.trim().toUpperCase(Locale.ROOT);
            Long count = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.roles WHERE code=?1")
                    .setParameter(1, code).getSingleResult()).longValue();
            if (count == 0) throw BusinessException.badRequest("ACCOUNT_ROLE_UNKNOWN", "Role " + code + " is not defined in the hospital role catalogue.");
            roleCodes.add(code);
        }
        if (roleCodes.isEmpty()) throw BusinessException.badRequest("ACCOUNT_ROLE_REQUIRED", "At least one role is required.");
        if (roleCodes.contains("DOCTOR")) {
            Long doctorCount = ((Number) em.createNativeQuery("""
                    SELECT count(*) FROM hospital.doctor_profiles dp
                    JOIN hospital.staff_members sm ON sm.id=dp.staff_id
                     WHERE dp.staff_id=?1 AND dp.is_active=TRUE AND sm.employment_status='ACTIVE'
                    """).setParameter(1, request.staffId).getSingleResult()).longValue();
            if (doctorCount == 0) throw BusinessException.badRequest("ACTIVE_DOCTOR_PROFILE_REQUIRED", "The DOCTOR role requires an active doctor profile linked to this staff member.");
        }

        UUID accountId = UUID.randomUUID();
        try {
            em.createNativeQuery("""
                    INSERT INTO hospital.user_accounts
                        (id, staff_id, username, password_hash, password_scheme, account_status, must_change_password,
                         password_changed_at)
                    VALUES (?1, ?2, ?3, ?4, 'PBKDF2-SHA256', 'ACTIVE', TRUE, NULL)
                    """)
                    .setParameter(1, accountId).setParameter(2, request.staffId)
                    .setParameter(3, username).setParameter(4, PasswordHasher.hash(request.initialPassword)).executeUpdate();
        } catch (PersistenceException exception) {
            if (hasSqlState(exception, "23505")) throw BusinessException.conflict("USERNAME_OR_STAFF_ALREADY_LINKED", "The username or staff member is already linked to an account.");
            throw exception;
        }
        for (String code : roleCodes) {
            em.createNativeQuery("""
                    INSERT INTO hospital.user_roles (user_id, role_id, assigned_by)
                    SELECT ?1, r.id, ?2 FROM hospital.roles r WHERE r.code=?3
                    """)
                    .setParameter(1, accountId).setParameter(2, actor.userId())
                    .setParameter(3, code).executeUpdate();
        }
        for (UUID branchId : branchIds) {
            em.createNativeQuery("INSERT INTO hospital.user_branch_access (user_id, branch_id, is_default) VALUES (?1, ?2, ?3)")
                    .setParameter(1, accountId).setParameter(2, branchId)
                    .setParameter(3, branchId.equals(request.defaultBranchId)).executeUpdate();
        }
        audit.recordAction("HOSPITAL_ACCOUNT_PROVISIONED", "USER_ACCOUNT", accountId, "roles=" + String.join(",", roleCodes), homeBranchId);
        return findAccount(accountId);
    }
    public AccountAdminResponse updateAccountStatus(UUID accountId, AccountStatusRequest request) {
        if (request == null || request.branchId == null || request.accountStatus == null) {
            throw BusinessException.badRequest("ACCOUNT_STATUS_INVALID", "Branch and account status are required.");
        }
        CurrentUser actor = requireAdmin(); users.requireBranchAccess(request.branchId);
        String status = request.accountStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) throw BusinessException.badRequest("ACCOUNT_STATUS_INVALID", "Supported account statuses are ACTIVE and DISABLED.");
        if (accountId.equals(actor.userId()) && "DISABLED".equals(status)) {
            throw BusinessException.conflict("CANNOT_DISABLE_CURRENT_ADMIN", "You cannot disable the account currently performing this operation.");
        }
        if ("ACTIVE".equals(status)) {
            String linkedEmploymentStatus = (String) em.createNativeQuery("""
                    SELECT sm.employment_status
                      FROM hospital.user_accounts ua
                      LEFT JOIN hospital.staff_members sm ON sm.id=ua.staff_id
                     WHERE ua.id=?1
                    """).setParameter(1, accountId).getResultStream().findFirst().orElse(null);
            if (linkedEmploymentStatus != null && !"ACTIVE".equals(linkedEmploymentStatus)) {
                throw BusinessException.conflict("STAFF_NOT_ACTIVE", "Reactivate the linked staff profile before enabling this account.");
            }
        }
        Long sharedBranch = ((Number) em.createNativeQuery("""
                SELECT count(*) FROM hospital.user_branch_access target
                JOIN hospital.user_branch_access actor ON actor.branch_id=target.branch_id
                 WHERE target.user_id=?1 AND actor.user_id=?2 AND target.branch_id=?3
                """)
                .setParameter(1, accountId).setParameter(2, actor.userId())
                .setParameter(3, request.branchId).getSingleResult()).longValue();
        if (sharedBranch == 0) throw BusinessException.notFound("Account in the selected branch");
        @SuppressWarnings("unchecked")
        List<?> targetBranchRows = em.createNativeQuery("SELECT branch_id FROM hospital.user_branch_access WHERE user_id=?1")
                .setParameter(1, accountId).getResultList();
        for (Object branchValue : targetBranchRows) {
            UUID targetBranch = DbValues.uuid(branchValue);
            if (!actor.branchIds().contains(targetBranch)) {
                throw BusinessException.forbidden("ACCOUNT_CROSS_BRANCH_ADMIN_REQUIRED", "Global account status changes require access to every branch assigned to that account.");
            }
        }
        if ("DISABLED".equals(status)) {
            Long isAdmin = ((Number) em.createNativeQuery("""
                    SELECT count(*) FROM hospital.user_roles ur
                    JOIN hospital.roles r ON r.id=ur.role_id AND r.code='HOSPITAL_ADMIN'
                     WHERE ur.user_id=?1
                    """)
                    .setParameter(1, accountId).getSingleResult()).longValue();
            if (isAdmin > 0) {
                // Serialize administrator-disable operations so two concurrent requests cannot
                // each observe the other as active and disable the final two administrators.
                em.createNativeQuery("SELECT id FROM hospital.roles WHERE code='HOSPITAL_ADMIN' FOR UPDATE")
                        .getSingleResult();
                Long otherAdmins = ((Number) em.createNativeQuery("""
                        SELECT count(DISTINCT ua.id)
                          FROM hospital.user_accounts ua
                          JOIN hospital.user_roles ur ON ur.user_id=ua.id
                          JOIN hospital.roles r ON r.id=ur.role_id AND r.code='HOSPITAL_ADMIN'
                         WHERE ua.account_status='ACTIVE' AND ua.id<>?1
                        """)
                        .setParameter(1, accountId).getSingleResult()).longValue();
                if (otherAdmins == 0) throw BusinessException.conflict("LAST_ADMIN_ACCOUNT", "The last active hospital administrator cannot be disabled.");
            }
        }
        int changed = em.createNativeQuery("UPDATE hospital.user_accounts SET account_status=?1, failed_login_count=0, locked_until=NULL, updated_at=now() WHERE id=?2")
                .setParameter(1, status).setParameter(2, accountId).executeUpdate();
        if (changed == 0) throw BusinessException.notFound("User account");
        audit.recordAction("HOSPITAL_ACCOUNT_STATUS_CHANGED", "USER_ACCOUNT", accountId, "status=" + status, request.branchId);
        return findAccount(accountId);
    }

    /**
     * Clears a locked-out account's TOTP secret, recovery codes and pending challenges,
     * and revokes its sessions. Used only after out-of-band identity verification.
     * Administrators cannot reset their own factor; that requires a second administrator
     * (or database recovery), which prevents a stolen admin session from removing MFA.
     */
    public void resetMfa(UUID accountId, UUID branchId, String reason) {
        if (branchId == null) {
            throw BusinessException.badRequest("MFA_RESET_BRANCH_REQUIRED", "The branch in which the account is visible is required.");
        }
        String cleanedReason = reason == null ? "" : reason.trim();
        if (cleanedReason.length() < 10 || cleanedReason.length() > 400) {
            throw BusinessException.badRequest("MFA_RESET_REASON_REQUIRED",
                    "A verification reason between 10 and 400 characters is required.");
        }
        CurrentUser actor = requireAdmin();
        users.requireBranchAccess(branchId);
        @SuppressWarnings("unchecked")
        List<?> rows = em.createNativeQuery("""
                SELECT ua.mfa_enrolled_at
                  FROM hospital.user_accounts ua
                  JOIN hospital.user_branch_access uba ON uba.user_id=ua.id AND uba.branch_id=?2
                 WHERE ua.id=?1
                """)
                .setParameter(1, accountId).setParameter(2, branchId).getResultList();
        if (rows.isEmpty()) throw BusinessException.notFound("Account in the selected branch");
        @SuppressWarnings("unchecked")
        List<?> targetBranchRows = em.createNativeQuery(
                        "SELECT branch_id FROM hospital.user_branch_access WHERE user_id=?1")
                .setParameter(1, accountId).getResultList();
        for (Object branchValue : targetBranchRows) {
            if (!actor.branchIds().contains(DbValues.uuid(branchValue))) {
                throw BusinessException.forbidden("ACCOUNT_CROSS_BRANCH_ADMIN_REQUIRED",
                        "Resetting the second factor requires access to every branch assigned to that account.");
            }
        }
        if (accountId.equals(actor.userId())) {
            throw BusinessException.conflict("CANNOT_RESET_OWN_MFA",
                    "Another hospital administrator must reset your second factor.");
        }
        Object enrolledAt = rows.get(0);
        if (enrolledAt instanceof Object[] row) enrolledAt = row[0];
        if (enrolledAt == null) {
            throw BusinessException.conflict("MFA_NOT_ENABLED", "This account does not have an active second factor.");
        }
        em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET mfa_secret = NULL, mfa_enabled = FALSE, mfa_enrolled_at = NULL,
                       mfa_last_used_step = NULL, updated_at = CURRENT_TIMESTAMP(6)
                 WHERE id = ?1
                """)
                .setParameter(1, accountId).executeUpdate();
        em.createNativeQuery("DELETE FROM hospital.user_mfa_recovery_codes WHERE user_id = ?1")
                .setParameter(1, accountId).executeUpdate();
        em.createNativeQuery("""
                UPDATE hospital.user_mfa_challenges
                   SET consumed_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ?1 AND consumed_at IS NULL
                """)
                .setParameter(1, accountId).executeUpdate();
        em.createNativeQuery("""
                UPDATE hospital.user_sessions
                   SET revoked_at = CURRENT_TIMESTAMP(6), revoke_reason = 'MFA_RESET_BY_ADMIN'
                 WHERE user_id = ?1 AND revoked_at IS NULL
                """)
                .setParameter(1, accountId).executeUpdate();
        audit.recordAction("HOSPITAL_ACCOUNT_MFA_RESET", "USER_ACCOUNT", accountId,
                "reason=" + cleanedReason.substring(0, Math.min(cleanedReason.length(), 200)), branchId);
    }

    private StaffResponse findStaff(UUID staffId, UUID branchId) {
        return listStaff(branchId, null, null, 200).stream().filter(item -> item.id.equals(staffId)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("Staff member"));
    }

    private AccountAdminResponse findAccount(UUID accountId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT ua.id, ua.staff_id, concat_ws(' ', sm.first_name, sm.last_name), ua.username,
                       ua.account_status, ua.must_change_password, ua.last_login_at, ua.created_at,
                       r.code, uba.branch_id, uba.is_default, ua.mfa_enrolled_at
                  FROM hospital.user_accounts ua
                  LEFT JOIN hospital.staff_members sm ON sm.id=ua.staff_id
                  LEFT JOIN hospital.user_roles ur ON ur.user_id=ua.id
                  LEFT JOIN hospital.roles r ON r.id=ur.role_id
                  LEFT JOIN hospital.user_branch_access uba ON uba.user_id=ua.id
                 WHERE ua.id=?1 ORDER BY r.code, uba.branch_id
                """).setParameter(1, accountId).getResultList();
        if (rows.isEmpty()) throw BusinessException.notFound("User account");
        Map<UUID, AccountAdminResponse> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID id = DbValues.uuid(row[0]);
            AccountAdminResponse dto = grouped.computeIfAbsent(id, ignored -> {
                AccountAdminResponse value = new AccountAdminResponse(); value.id = id; value.staffId = DbValues.uuid(row[1]);
                value.staffName = (String) row[2]; value.username = (String) row[3]; value.accountStatus = (String) row[4];
                value.mustChangePassword = Boolean.TRUE.equals(row[5]); value.lastLoginAt = instant(row[6]);
                value.createdAt = instant(row[7]); value.mfaEnabled = row[11] != null; return value;
            });
            if (row[8] != null && !dto.roleCodes.contains((String) row[8])) dto.roleCodes.add((String) row[8]);
            if (row[9] != null) {
                UUID branch = DbValues.uuid(row[9]); if (!dto.branchIds.contains(branch)) dto.branchIds.add(branch);
                if (Boolean.TRUE.equals(row[10])) dto.defaultBranchId = branch;
            }
        }
        return grouped.values().iterator().next();
    }

    private void requireActiveDepartment(UUID departmentId, UUID branchId) {
        Long count = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.departments WHERE id=?1 AND branch_id=?2 AND is_active=TRUE")
                .setParameter(1, departmentId).setParameter(2, branchId).getSingleResult()).longValue();
        if (count == 0) throw BusinessException.badRequest("DEPARTMENT_BRANCH_MISMATCH", "The selected department is not active in the selected branch.");
    }

    private CurrentUser requireAdmin() {
        CurrentUser actor = users.requireCurrentUser();
        if (!actor.roleCodes().contains("HOSPITAL_ADMIN")) throw BusinessException.forbidden("HOSPITAL_ADMIN_REQUIRED", "Hospital administrator access is required.");
        return actor;
    }

    private StaffResponse staffResponse(Object[] row) {
        StaffResponse dto = new StaffResponse(); dto.id = DbValues.uuid(row[0]); dto.branchId = DbValues.uuid(row[1]);
        dto.branchName = (String) row[2]; dto.departmentId = DbValues.uuid(row[3]); dto.departmentName = (String) row[4];
        dto.employeeNumber = (String) row[5]; dto.firstName = (String) row[6]; dto.lastName = (String) row[7];
        dto.workEmail = (String) row[8]; dto.phoneNumber = (String) row[9]; dto.jobTitle = (String) row[10];
        dto.employmentStatus = (String) row[11]; dto.hiredOn = localDate(row[12]);
        dto.linkedUsername = (String) row[13]; return dto;
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ON_LEAVE", "SUSPENDED", "TERMINATED").contains(value)) {
            throw BusinessException.badRequest("EMPLOYMENT_STATUS_INVALID", "Unsupported employment status filter.");
        }
        return value;
    }
    private String cleanRequired(String value, String field, int max) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest("STAFF_FIELD_REQUIRED", field + " is required.");
        String cleaned = value.trim();
        if (cleaned.length() > max) throw BusinessException.badRequest("STAFF_FIELD_TOO_LONG", field + " exceeds its maximum length.");
        return cleaned;
    }
    private String cleanOptional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > max) throw BusinessException.badRequest("STAFF_FIELD_TOO_LONG", "A staff contact field exceeds its maximum length.");
        return cleaned;
    }
    private LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof Date date) return date.toLocalDate();
        throw new IllegalStateException("Unexpected date value type: " + value.getClass().getName());
    }
    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        throw new IllegalStateException("Unexpected timestamp value type: " + value.getClass().getName());
    }
    private boolean hasSqlState(Throwable exception, String state) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sql && state.equals(sql.getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }
}
