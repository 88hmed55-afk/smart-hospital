package com.smarthospital.security.service;

import com.smarthospital.governance.dto.AuditIntegrityReport;
import com.smarthospital.governance.service.AuditIntegrityService;
import com.smarthospital.security.auth.SecuritySettings;
import com.smarthospital.security.dto.SecurityMetricsResponse;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Produces the security metrics consumed by the operations dashboard and by
 * monitoring probes. All values are database aggregates; no PHI is read.
 */
@Stateless
public class SecurityMetricsService {

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private AuditIntegrityService auditIntegrityService;

    public SecurityMetricsResponse current(boolean httpsRequest) {
        SecurityMetricsResponse metrics = new SecurityMetricsResponse();
        metrics.generatedAt = Instant.now();
        metrics.httpsRequest = httpsRequest;
        metrics.forceHttpsEnabled = SecuritySettings.forceHttps();
        metrics.passwordRotationEnforced = SecuritySettings.enforcePasswordRotation();
        metrics.mfaEnrollmentEnforced = SecuritySettings.mfaEnrollmentEnforced();
        metrics.mfaRequiredRoles = List.copyOf(SecuritySettings.mfaRequiredRoles());

        metrics.activeSessions = count("""
                SELECT count(*) FROM hospital.user_sessions
                 WHERE revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP(6)
                """);
        metrics.lockedAccounts = count("""
                SELECT count(*) FROM hospital.user_accounts
                 WHERE locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP(6)
                """);
        metrics.accountsPendingPasswordChange = count("""
                SELECT count(*) FROM hospital.user_accounts
                 WHERE must_change_password = TRUE AND account_status = 'ACTIVE'
                """);
        metrics.mfaEnabledAccounts = count("""
                SELECT count(*) FROM hospital.user_accounts
                 WHERE mfa_enrolled_at IS NOT NULL AND account_status = 'ACTIVE'
                """);
        Set<String> requiredRoles = SecuritySettings.mfaRequiredRoles();
        long pendingMfa = 0;
        for (String role : requiredRoles) {
            pendingMfa += countWithRole("""
                    SELECT count(DISTINCT ua.id)
                      FROM hospital.user_accounts ua
                      JOIN hospital.user_roles ur ON ur.user_id = ua.id
                      JOIN hospital.roles r ON r.id = ur.role_id
                     WHERE ua.account_status = 'ACTIVE'
                       AND ua.mfa_enrolled_at IS NULL
                       AND r.code = ?1
                    """, role);
        }
        metrics.accountsPendingMfaEnrollment = pendingMfa;

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        metrics.failedLogins24h = countWithSince("""
                SELECT count(*) FROM hospital.audit_logs
                 WHERE action_code = 'LOGIN_FAILED' AND occurred_at >= ?1
                """, since);
        metrics.successfulLogins24h = countWithSince("""
                SELECT count(*) FROM hospital.audit_logs
                 WHERE action_code = 'LOGIN_SUCCESS' AND occurred_at >= ?1
                """, since);
        metrics.mfaChallengesIssued24h = countWithSince("""
                SELECT count(*) FROM hospital.audit_logs
                 WHERE action_code = 'MFA_CHALLENGE_ISSUED' AND occurred_at >= ?1
                """, since);
        metrics.mfaVerifications24h = countWithSince("""
                SELECT count(*) FROM hospital.audit_logs
                 WHERE action_code = 'MFA_VERIFIED' AND occurred_at >= ?1
                """, since);
        metrics.mfaFailures24h = countWithSince("""
                SELECT count(*) FROM hospital.audit_logs
                 WHERE action_code IN ('MFA_FAILED', 'MFA_ENROLLMENT_FAILED') AND occurred_at >= ?1
                """, since);

        @SuppressWarnings("unchecked")
        var last = em.createNativeQuery("""
                SELECT MAX(occurred_at) FROM hospital.audit_logs
                 WHERE action_code = 'LOGIN_SUCCESS'
                """).getResultList();
        if (!last.isEmpty() && last.get(0) != null) {
            Object value = last.get(0);
            if (value instanceof Timestamp ts) metrics.lastSuccessfulLoginAt = ts.toInstant();
            else if (value instanceof java.time.LocalDateTime ldt) {
                metrics.lastSuccessfulLoginAt = ldt.toInstant(java.time.ZoneOffset.UTC);
            }
        }

        AuditIntegrityReport integrity = auditIntegrityService.verify();
        metrics.auditChainIntact = integrity.chainIntact;
        metrics.auditChainRows = integrity.chainRows;
        metrics.auditHashMismatches = integrity.hashMismatches;
        return metrics;
    }

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private long countWithSince(String sql, Instant since) {
        return ((Number) em.createNativeQuery(sql)
                .setParameter(1, Timestamp.from(since))
                .getSingleResult()).longValue();
    }

    private long countWithRole(String sql, String role) {
        return ((Number) em.createNativeQuery(sql)
                .setParameter(1, role)
                .getSingleResult()).longValue();
    }
}
