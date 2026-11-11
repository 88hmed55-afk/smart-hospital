package com.smarthospital.security.service;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.security.dto.MaintenanceResult;
import jakarta.ejb.EJB;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Nightly hygiene for authentication state.
 *
 * <p>Opaque bearer sessions accumulate forever unless they are pruned, and a lockout
 * that expired should not keep inflating the "locked accounts" security metric. This
 * singleton runs once a day and:</p>
 * <ul>
 *   <li>deletes sessions that expired or were revoked more than the retention window
 *       ago (recent rows are kept for forensic review);</li>
 *   <li>clears failed-login counters and expired lockouts;</li>
 *   <li>writes a single chained {@code SYSTEM_MAINTENANCE} audit event when it
 *       actually changed something.</li>
 * </ul>
 *
 * <p>The job never touches active sessions, never reads PHI, and can be disabled with
 * {@code -Dsmarthospital.maintenance.enabled=false} or
 * {@code SMART_HOSPITAL_MAINTENANCE_ENABLED=false}. Retention defaults to 30 days and
 * can be tuned with {@code -Dsmarthospital.maintenance.sessionRetentionDays=N}.</p>
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class SessionMaintenanceService {

    private static final int DEFAULT_RETENTION_DAYS = 30;

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private AuditService auditService;

    @Schedule(hour = "2", minute = "15", second = "0", persistent = false)
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void scheduledCleanup() {
        runCleanup();
    }

    /**
     * Runs the cleanup immediately. Exposed to operations through
     * {@code POST /api/v1/system/maintenance/cleanup} (HOSPITAL_ADMIN only).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public MaintenanceResult runCleanup() {
        MaintenanceResult result = new MaintenanceResult();
        result.executedAt = Instant.now();
        result.enabled = enabled();
        result.retentionDays = retentionDays();
        if (!result.enabled) {
            return result;
        }

        Timestamp cutoff = Timestamp.from(
                Instant.now().minus(result.retentionDays, ChronoUnit.DAYS));

        result.prunedSessions = em.createNativeQuery("""
                DELETE FROM hospital.user_sessions
                 WHERE (expires_at < CURRENT_TIMESTAMP(6) OR revoked_at IS NOT NULL)
                   AND COALESCE(revoked_at, expires_at) < ?1
                """)
                .setParameter(1, cutoff)
                .executeUpdate();

        result.prunedMfaChallenges = em.createNativeQuery("""
                DELETE FROM hospital.user_mfa_challenges
                 WHERE (expires_at < CURRENT_TIMESTAMP(6) OR consumed_at IS NOT NULL)
                   AND COALESCE(consumed_at, expires_at) < ?1
                """)
                .setParameter(1, cutoff)
                .executeUpdate();

        result.releasedLockouts = em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET failed_login_count = 0,
                       locked_until = NULL,
                       version = version + 1,
                       updated_at = CURRENT_TIMESTAMP(6)
                 WHERE locked_until IS NOT NULL
                   AND locked_until <= CURRENT_TIMESTAMP(6)
                """)
                .executeUpdate();

        if (result.prunedSessions > 0 || result.prunedMfaChallenges > 0 || result.releasedLockouts > 0) {
            auditService.recordSystemAction(
                    "SYSTEM_MAINTENANCE",
                    "AUTHENTICATION_STATE",
                    "SUCCESS",
                    "Pruned " + result.prunedSessions + " stale session(s), "
                            + result.prunedMfaChallenges + " stale MFA challenge(s) and released "
                            + result.releasedLockouts + " expired lockout(s); retained sessions newer than "
                            + result.retentionDays + " days.");
        }
        return result;
    }

    private static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(
                "smarthospital.maintenance.enabled",
                System.getenv().getOrDefault("SMART_HOSPITAL_MAINTENANCE_ENABLED", "true")));
    }

    private static int retentionDays() {
        try {
            int configured = Integer.parseInt(System.getProperty(
                    "smarthospital.maintenance.sessionRetentionDays",
                    System.getenv().getOrDefault("SMART_HOSPITAL_SESSION_RETENTION_DAYS",
                            String.valueOf(DEFAULT_RETENTION_DAYS))));
            return configured < 0 ? DEFAULT_RETENTION_DAYS : configured;
        } catch (NumberFormatException ex) {
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
