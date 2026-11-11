package com.smarthospital.security.auth;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.common.persistence.DbValues;
import com.smarthospital.config.RequestMetadata;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.mfa.MfaService;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application-level authentication: password verification, lockout, TOTP MFA,
 * opaque bearer sessions and password rotation. Replaces the container File
 * Realm in production.
 */
@Stateless
public class AuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);
    private static final Duration LAST_SEEN_RESOLUTION = Duration.ofSeconds(60);

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService currentUserService;

    @EJB
    private AuditService auditService;

    @EJB
    private MfaService mfaService;

    @Inject
    private RequestMetadata requestMetadata;

    /**
     * Result of the password step. When {@code mfaRequired} is true the token is null
     * and only {@code mfaChallengeToken} may be used, against {@code /auth/mfa/verify}.
     */
    public record AuthSession(String token, Instant expiresAt, boolean mustChangePassword,
                              CurrentUser user, boolean mfaRequired, String mfaChallengeToken,
                              boolean mfaEnrollmentRequired) {
        public static AuthSession challenge(String challengeToken, boolean mustChangePassword,
                                            boolean mfaEnrollmentRequired) {
            return new AuthSession(null, null, mustChangePassword, null,
                    true, challengeToken, mfaEnrollmentRequired);
        }
    }

    private record IssuedSession(String token, Instant expiresAt) { }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public AuthSession login(String username, String password) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank() || normalized.length() > 80 || password == null || password.isEmpty()) {
            auditService.recordAuthenticationEvent(null, normalized, "LOGIN_FAILED", "FAILURE", "Missing credentials.");
            throw BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, password_hash, account_status, must_change_password,
                       failed_login_count, locked_until, staff_id, mfa_enrolled_at
                  FROM hospital.user_accounts
                 WHERE lower(username) = lower(?1)
                """)
                .setParameter(1, normalized)
                .getResultList();

        if (rows.isEmpty()) {
            // Equalise timing so account enumeration is not possible.
            PasswordHasher.verify(password, PASSWORD_DUMMY);
            auditService.recordAuthenticationEvent(null, normalized, "LOGIN_FAILED", "FAILURE", "Unknown account.");
            throw BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }

        Object[] row = rows.get(0);
        UUID userId = DbValues.uuid(row[0]);
        String passwordHash = (String) row[1];
        String status = (String) row[2];
        boolean mustChangePassword = Boolean.TRUE.equals(row[3]) || Integer.valueOf(1).equals(row[3]);
        int failedCount = ((Number) row[4]).intValue();
        Instant lockedUntil = instant(row[5]);
        boolean mfaEnabled = row[7] != null;

        boolean locked = lockedUntil != null && lockedUntil.isAfter(Instant.now());
        if (locked || !"ACTIVE".equals(status)) {
            auditService.recordAuthenticationEvent(userId, normalized,
                    locked ? "LOGIN_BLOCKED_LOCKED" : "LOGIN_BLOCKED_INACTIVE", "DENIED",
                    locked ? "Account temporarily locked." : "Account is not active.");
            throw BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }

        if (!PasswordHasher.verify(password, passwordHash)) {
            int nextCount = failedCount + 1;
            Instant nextLock = nextCount >= MAX_FAILED_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            em.createNativeQuery("""
                    UPDATE hospital.user_accounts
                       SET failed_login_count = ?2,
                           locked_until = ?3
                     WHERE id = ?1
                    """)
                    .setParameter(1, userId)
                    .setParameter(2, nextCount)
                    .setParameter(3, nextLock == null ? null : Timestamp.from(nextLock))
                    .executeUpdate();
            auditService.recordAuthenticationEvent(userId, normalized,
                    nextLock == null ? "LOGIN_FAILED" : "LOGIN_LOCKED", "FAILURE",
                    nextLock == null ? "Invalid password." : "Too many failed attempts; account locked.");
            throw BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }

        em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET failed_login_count = 0,
                       locked_until = NULL,
                       last_login_at = CURRENT_TIMESTAMP(6)
                 WHERE id = ?1
                """)
                .setParameter(1, userId)
                .executeUpdate();

        CurrentUser user = currentUserService.resolveActiveUser(normalized);
        if (mfaEnabled) {
            String challenge = mfaService.createChallenge(userId, normalized);
            auditService.recordAuthenticationEvent(userId, normalized, "LOGIN_PASSWORD_VERIFIED", "SUCCESS",
                    "Password accepted; MFA challenge issued.");
            return AuthSession.challenge(challenge, mustChangePassword, user.mfaEnrollmentRequired());
        }

        IssuedSession issued = issueSession(userId);
        auditService.recordAuthenticationEvent(userId, normalized, "LOGIN_SUCCESS", "SUCCESS",
                user.mfaEnrollmentRequired()
                        ? "Bearer session created; MFA enrolment is required by policy."
                        : "Bearer session created.");
        return new AuthSession(issued.token(), issued.expiresAt(), mustChangePassword, user,
                false, null, user.mfaEnrollmentRequired());
    }

    /** Completes the second login step and issues the bearer session. */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public AuthSession verifyMfa(String challengeToken, String code) {
        MfaService.Verification verified = mfaService.verifyChallenge(challengeToken, code);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT account_status, must_change_password, locked_until
                  FROM hospital.user_accounts
                 WHERE id = ?1
                """)
                .setParameter(1, verified.userId())
                .getResultList();
        if (rows.isEmpty()) {
            throw BusinessException.unauthorized("MFA_CHALLENGE_INVALID",
                    "The verification session is invalid or has expired. Sign in again.");
        }
        Object[] row = rows.get(0);
        String status = (String) row[0];
        boolean mustChangePassword = Boolean.TRUE.equals(row[1]) || Integer.valueOf(1).equals(row[1]);
        Instant lockedUntil = instant(row[2]);
        if (!"ACTIVE".equals(status) || (lockedUntil != null && lockedUntil.isAfter(Instant.now()))) {
            auditService.recordAuthenticationEvent(verified.userId(), verified.username(),
                    "LOGIN_BLOCKED_INACTIVE", "DENIED", "MFA succeeded but the account is not usable.");
            throw BusinessException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password.");
        }
        IssuedSession issued = issueSession(verified.userId());
        CurrentUser user = currentUserService.resolveActiveUser(verified.username());
        auditService.recordAuthenticationEvent(verified.userId(), verified.username(),
                "LOGIN_SUCCESS", "SUCCESS", "Bearer session created after " + verified.method() + " verification.");
        return new AuthSession(issued.token(), issued.expiresAt(), mustChangePassword, user,
                false, null, user.mfaEnrollmentRequired());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public CurrentUser validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) return null;
        String tokenHash = TokenService.hash(rawToken);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT s.id, s.created_at, s.last_seen_at, s.expires_at, s.revoked_at,
                       ua.username, ua.account_status
                  FROM hospital.user_sessions s
                  JOIN hospital.user_accounts ua ON ua.id = s.user_id
                 WHERE s.token_hash = ?1
                """)
                .setParameter(1, tokenHash)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        Instant createdAt = instant(row[1]);
        Instant lastSeen = instant(row[2]);
        Instant expiresAt = instant(row[3]);
        Instant revokedAt = instant(row[4]);
        String username = (String) row[5];
        String status = (String) row[6];
        Instant now = Instant.now();
        if (revokedAt != null || !"ACTIVE".equals(status) || expiresAt == null || !expiresAt.isAfter(now)
                || createdAt == null || now.isAfter(createdAt.plus(ABSOLUTE_TIMEOUT))) {
            return null;
        }
        if (lastSeen == null || now.isAfter(lastSeen.plus(LAST_SEEN_RESOLUTION))) {
            em.createNativeQuery("""
                    UPDATE hospital.user_sessions
                       SET last_seen_at = ?2, expires_at = ?3
                     WHERE id = ?1 AND revoked_at IS NULL
                    """)
                    .setParameter(1, DbValues.uuid(row[0]))
                    .setParameter(2, Timestamp.from(now))
                    .setParameter(3, Timestamp.from(now.plus(IDLE_TIMEOUT)))
                    .executeUpdate();
        }
        try {
            return currentUserService.resolveActiveUser(username);
        } catch (BusinessException ex) {
            return null;
        }
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String tokenHash = TokenService.hash(rawToken);
        int changed = em.createNativeQuery("""
                UPDATE hospital.user_sessions
                   SET revoked_at = CURRENT_TIMESTAMP(6), revoke_reason = 'USER_LOGOUT'
                 WHERE token_hash = ?1 AND revoked_at IS NULL
                """)
                .setParameter(1, tokenHash)
                .executeUpdate();
        if (changed > 0) {
            CurrentUser user = currentUserService.requireCurrentUser();
            auditService.recordAuthenticationEvent(user.userId(), user.username(), "LOGOUT", "SUCCESS", "Bearer session revoked.");
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void changePassword(String currentPassword, String newPassword) {
        CurrentUser user = currentUserService.requireCurrentUser();
        if (!PasswordHasher.isStrong(newPassword)) {
            throw BusinessException.badRequest("PASSWORD_POLICY_VIOLATION",
                    "Password must be 12-128 characters and include upper case, lower case, a digit and a symbol.");
        }
        String currentHash = passwordHash(user.userId());
        if (currentHash == null || !PasswordHasher.verify(currentPassword, currentHash)) {
            auditService.recordAuthenticationEvent(user.userId(), user.username(), "PASSWORD_CHANGE_FAILED", "DENIED", "Current password mismatch.");
            throw BusinessException.unauthorized("CURRENT_PASSWORD_INVALID", "The current password is incorrect.");
        }
        if (currentPassword != null && currentPassword.equals(newPassword)) {
            throw BusinessException.badRequest("PASSWORD_REUSED", "The new password must differ from the current password.");
        }
        em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET password_hash = ?2,
                       password_scheme = 'PBKDF2-SHA256',
                       password_changed_at = CURRENT_TIMESTAMP(6),
                       must_change_password = FALSE,
                       failed_login_count = 0,
                       locked_until = NULL
                 WHERE id = ?1
                """)
                .setParameter(1, user.userId())
                .setParameter(2, PasswordHasher.hash(newPassword))
                .executeUpdate();
        // Revoke every other active session: a password change invalidates prior tokens.
        em.createNativeQuery("""
                UPDATE hospital.user_sessions
                   SET revoked_at = CURRENT_TIMESTAMP(6), revoke_reason = 'PASSWORD_CHANGED'
                 WHERE user_id = ?1 AND revoked_at IS NULL
                """)
                .setParameter(1, user.userId())
                .executeUpdate();
        auditService.recordAuthenticationEvent(user.userId(), user.username(), "PASSWORD_CHANGED", "SUCCESS", "Credential rotated; all sessions revoked.");
    }

    public void revokeAllSessions(UUID userId, String reason) {
        em.createNativeQuery("""
                UPDATE hospital.user_sessions
                   SET revoked_at = CURRENT_TIMESTAMP(6), revoke_reason = ?2
                 WHERE user_id = ?1 AND revoked_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, truncate(reason, 80))
                .executeUpdate();
    }

    public MfaService.EnrollmentStart beginMfaEnrollment() {
        CurrentUser user = currentUserService.requireCurrentUser();
        return mfaService.beginEnrollment(user.userId(), user.username());
    }

    public List<String> confirmMfaEnrollment(String code) {
        CurrentUser user = currentUserService.requireCurrentUser();
        return mfaService.confirmEnrollment(user.userId(), user.username(), code);
    }

    public MfaService.Status mfaStatus() {
        CurrentUser user = currentUserService.requireCurrentUser();
        return mfaService.status(user.userId());
    }

    /**
     * Disabling MFA requires the password plus a current factor. Every session is
     * revoked afterwards, so the next request must authenticate from scratch.
     */
    public void disableMfa(String password, String code) {
        CurrentUser user = currentUserService.requireCurrentUser();
        requirePassword(user, password, "MFA_DISABLE_FAILED");
        mfaService.disable(user.userId(), user.username(), code);
        revokeAllSessions(user.userId(), "MFA_DISABLED");
    }

    /** Recovery codes can only be rotated with the password and a current TOTP code. */
    public List<String> regenerateRecoveryCodes(String password, String code) {
        CurrentUser user = currentUserService.requireCurrentUser();
        requirePassword(user, password, "MFA_RECOVERY_CODES_FAILED");
        return mfaService.regenerateRecoveryCodes(user.userId(), user.username(), code);
    }

    private void requirePassword(CurrentUser user, String password, String failureAction) {
        String currentHash = passwordHash(user.userId());
        if (currentHash == null || !PasswordHasher.verify(password, currentHash)) {
            auditService.recordAuthenticationEvent(user.userId(), user.username(), failureAction, "DENIED",
                    "Password re-authentication failed.");
            throw BusinessException.unauthorized("CURRENT_PASSWORD_INVALID", "The current password is incorrect.");
        }
    }

    private String passwordHash(UUID userId) {
        @SuppressWarnings("unchecked")
        List<?> rows = em.createNativeQuery(
                        "SELECT password_hash FROM hospital.user_accounts WHERE id = ?1")
                .setParameter(1, userId)
                .getResultList();
        if (rows.isEmpty()) return null;
        Object stored = rows.get(0);
        return stored instanceof Object[] row ? (String) row[0] : (String) stored;
    }

    private IssuedSession issueSession(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(IDLE_TIMEOUT);
        String rawToken = TokenService.newToken();
        em.createNativeQuery("""
                INSERT INTO hospital.user_sessions
                    (id, user_id, token_hash, ip_address, user_agent, created_at, last_seen_at, expires_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6, ?7)
                """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, userId)
                .setParameter(3, TokenService.hash(rawToken))
                .setParameter(4, truncate(requestMetadata == null ? null : requestMetadata.getClientIp(), 45))
                .setParameter(5, truncate(requestMetadata == null ? null : requestMetadata.getUserAgent(), 400))
                .setParameter(6, Timestamp.from(now))
                .setParameter(7, Timestamp.from(expiresAt))
                .executeUpdate();
        return new IssuedSession(rawToken, expiresAt);
    }

    private static final String PASSWORD_DUMMY = PasswordHasher.dummyHash();

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
