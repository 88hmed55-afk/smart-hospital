package com.smarthospital.security.mfa;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.common.persistence.DbValues;
import com.smarthospital.config.RequestMetadata;
import com.smarthospital.security.auth.SecuritySettings;
import com.smarthospital.security.auth.TokenService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * TOTP enrolment, login challenges and one-time recovery codes.
 *
 * <p>A password-verified login creates a short-lived challenge row and returns the
 * raw challenge token once; only its SHA-256 hash is stored. The session bearer
 * token is issued only after a valid TOTP code (or an unused recovery code) is
 * presented. Five invalid codes invalidate the challenge, and a TOTP code cannot
 * be reused because the last accepted time step is stored on the account.</p>
 */
@Stateless
public class MfaService {
    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private static final int MAX_CHALLENGE_ATTEMPTS = 5;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private AuditService auditService;

    @Inject
    private RequestMetadata requestMetadata;

    public record EnrollmentStart(String secret, String otpauthUri, String issuer, String account) { }

    public record Verification(UUID userId, String username, String method) { }

    public record Status(boolean enabled, long recoveryCodesRemaining, Instant enrolledAt) { }

    public EnrollmentStart beginEnrollment(UUID userId, String username) {
        String secret = TotpService.generateSecret();
        Instant now = Instant.now();
        int updated = em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET mfa_secret = ?2,
                       mfa_enabled = FALSE,
                       mfa_enrolled_at = NULL,
                       mfa_last_used_step = NULL,
                       updated_at = ?3
                 WHERE id = ?1 AND mfa_enrolled_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, SecretCipher.encrypt(secret))
                .setParameter(3, Timestamp.from(now))
                .executeUpdate();
        if (updated == 0) {
            throw BusinessException.conflict("MFA_ALREADY_ENABLED",
                    "Multi-factor authentication is already active for this account.");
        }
        auditService.recordAuthenticationEvent(userId, username, "MFA_ENROLLMENT_STARTED", "SUCCESS",
                "A new TOTP secret was generated; enrolment is pending confirmation.");
        String issuer = SecuritySettings.mfaIssuer();
        return new EnrollmentStart(secret, TotpService.otpauthUri(issuer, username, secret), issuer, username);
    }

    public List<String> confirmEnrollment(UUID userId, String username, String code) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT mfa_secret, mfa_enrolled_at
                  FROM hospital.user_accounts
                 WHERE id = ?1
                """)
                .setParameter(1, userId)
                .getResultList();
        if (rows.isEmpty()) {
            throw BusinessException.notFound("The hospital account was not found.");
        }
        Object[] row = rows.get(0);
        String encrypted = (String) row[0];
        if (encrypted == null || encrypted.isBlank()) {
            throw BusinessException.badRequest("MFA_NOT_STARTED",
                    "Start MFA enrolment before confirming a code.");
        }
        if (row[1] != null) {
            throw BusinessException.conflict("MFA_ALREADY_ENABLED",
                    "Multi-factor authentication is already active for this account.");
        }
        String secret = decrypt(encrypted);
        OptionalLong step = TotpService.verify(secret, code, null);
        if (step.isEmpty()) {
            auditService.recordAuthenticationEvent(userId, username, "MFA_ENROLLMENT_FAILED", "DENIED",
                    "The confirmation code was invalid.");
            throw BusinessException.badRequest("MFA_CODE_INVALID",
                    "The authenticator code is invalid. Check the device clock and try again.");
        }
        Instant now = Instant.now();
        int updated = em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET mfa_enrolled_at = ?2,
                       mfa_enabled = TRUE,
                       mfa_last_used_step = ?3,
                       updated_at = ?2
                 WHERE id = ?1 AND mfa_enrolled_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, Timestamp.from(now))
                .setParameter(3, step.getAsLong())
                .executeUpdate();
        if (updated == 0) {
            throw BusinessException.conflict("MFA_ALREADY_ENABLED",
                    "Multi-factor authentication was enabled by another request.");
        }
        List<String> recoveryCodes = replaceRecoveryCodes(userId, now);
        auditService.recordAuthenticationEvent(userId, username, "MFA_ENABLED", "SUCCESS",
                "TOTP enrolment confirmed; " + recoveryCodes.size() + " recovery codes issued.");
        return recoveryCodes;
    }

    public String disable(UUID userId, String username, String code) {
        List<Object[]> rows = accountSecurityRow(userId);
        if (rows.isEmpty()) throw BusinessException.notFound("The hospital account was not found.");
        Object[] row = rows.get(0);
        String encrypted = (String) row[0];
        if (encrypted == null || row[1] == null) {
            throw BusinessException.badRequest("MFA_NOT_ENABLED", "Multi-factor authentication is not active.");
        }
        String secret = decrypt(encrypted);
        String method = verifySecondFactor(userId, secret, longValue(row[2]), code);
        if (method == null) {
            auditService.recordAuthenticationEvent(userId, username, "MFA_DISABLE_FAILED", "DENIED",
                    "The authenticator or recovery code was invalid.");
            throw BusinessException.badRequest("MFA_CODE_INVALID",
                    "A valid authenticator or recovery code is required to disable MFA.");
        }
        Instant now = Instant.now();
        em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET mfa_secret = NULL,
                       mfa_enabled = FALSE,
                       mfa_enrolled_at = NULL,
                       mfa_last_used_step = NULL,
                       updated_at = ?2
                 WHERE id = ?1
                """)
                .setParameter(1, userId)
                .setParameter(2, Timestamp.from(now))
                .executeUpdate();
        em.createNativeQuery("DELETE FROM hospital.user_mfa_recovery_codes WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
        em.createNativeQuery("""
                UPDATE hospital.user_mfa_challenges
                   SET consumed_at = ?2
                 WHERE user_id = ?1 AND consumed_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, Timestamp.from(now))
                .executeUpdate();
        auditService.recordAuthenticationEvent(userId, username, "MFA_DISABLED", "SUCCESS",
                "Multi-factor authentication disabled after " + method + " verification.");
        return method;
    }

    public List<String> regenerateRecoveryCodes(UUID userId, String username, String code) {
        List<Object[]> rows = accountSecurityRow(userId);
        if (rows.isEmpty()) throw BusinessException.notFound("The hospital account was not found.");
        Object[] row = rows.get(0);
        String encrypted = (String) row[0];
        if (encrypted == null || row[1] == null) {
            throw BusinessException.badRequest("MFA_NOT_ENABLED", "Multi-factor authentication is not active.");
        }
        String secret = decrypt(encrypted);
        OptionalLong step = TotpService.verify(secret, code, longValue(row[2]));
        if (step.isEmpty()) {
            auditService.recordAuthenticationEvent(userId, username, "MFA_RECOVERY_CODES_FAILED", "DENIED",
                    "A valid authenticator code is required to regenerate recovery codes.");
            throw BusinessException.badRequest("MFA_CODE_INVALID",
                    "A valid authenticator code is required to regenerate recovery codes.");
        }
        Instant now = Instant.now();
        em.createNativeQuery("""
                UPDATE hospital.user_accounts
                   SET mfa_last_used_step = ?2, updated_at = ?3
                 WHERE id = ?1
                """)
                .setParameter(1, userId)
                .setParameter(2, step.getAsLong())
                .setParameter(3, Timestamp.from(now))
                .executeUpdate();
        List<String> codes = replaceRecoveryCodes(userId, now);
        auditService.recordAuthenticationEvent(userId, username, "MFA_RECOVERY_CODES_REGENERATED", "SUCCESS",
                codes.size() + " new recovery codes issued; all previous codes invalidated.");
        return codes;
    }

    public Status status(UUID userId) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT mfa_enrolled_at FROM hospital.user_accounts WHERE id = ?1
                """)
                .setParameter(1, userId)
                .getResultList();
        if (rows.isEmpty()) throw BusinessException.notFound("The hospital account was not found.");
        Object value = rows.get(0);
        if (value instanceof Object[] row) value = row[0];
        Instant enrolledAt = instant(value);
        long remaining = ((Number) em.createNativeQuery("""
                SELECT count(*) FROM hospital.user_mfa_recovery_codes
                 WHERE user_id = ?1 AND used_at IS NULL
                """)
                .setParameter(1, userId)
                .getSingleResult()).longValue();
        return new Status(enrolledAt != null, remaining, enrolledAt);
    }

    /** Creates a login challenge and returns the raw token (stored only as a hash). */
    public String createChallenge(UUID userId, String username) {
        Instant now = Instant.now();
        em.createNativeQuery("""
                UPDATE hospital.user_mfa_challenges
                   SET consumed_at = ?2
                 WHERE user_id = ?1 AND consumed_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, Timestamp.from(now))
                .executeUpdate();
        String rawToken = "shm_" + TokenService.newChallengeToken();
        em.createNativeQuery("""
                INSERT INTO hospital.user_mfa_challenges
                    (id, user_id, token_hash, ip_address, user_agent, attempts, created_at, expires_at)
                VALUES (?1, ?2, ?3, ?4, ?5, 0, ?6, ?7)
                """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, userId)
                .setParameter(3, TokenService.hash(rawToken))
                .setParameter(4, truncate(requestMetadata == null ? null : requestMetadata.getClientIp(), 45))
                .setParameter(5, truncate(requestMetadata == null ? null : requestMetadata.getUserAgent(), 400))
                .setParameter(6, Timestamp.from(now))
                .setParameter(7, Timestamp.from(now.plus(CHALLENGE_LIFETIME)))
                .executeUpdate();
        auditService.recordAuthenticationEvent(userId, username, "MFA_CHALLENGE_ISSUED", "SUCCESS",
                "Password accepted; a five-minute TOTP challenge was issued.");
        return rawToken;
    }

    /** Verifies a login challenge and returns the identity that may now receive a session. */
    public Verification verifyChallenge(String rawToken, String code) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            throw BusinessException.unauthorized("MFA_CHALLENGE_INVALID",
                    "The verification session is invalid or has expired. Sign in again.");
        }
        List<Object[]> rows = em.createNativeQuery("""
                SELECT c.id, c.user_id, c.attempts, c.expires_at, c.consumed_at,
                       ua.username, ua.account_status, ua.mfa_secret,
                       ua.mfa_enrolled_at, ua.mfa_last_used_step
                  FROM hospital.user_mfa_challenges c
                  JOIN hospital.user_accounts ua ON ua.id = c.user_id
                 WHERE c.token_hash = ?1
                """)
                .setParameter(1, TokenService.hash(rawToken))
                .getResultList();
        if (rows.isEmpty()) {
            throw BusinessException.unauthorized("MFA_CHALLENGE_INVALID",
                    "The verification session is invalid or has expired. Sign in again.");
        }
        Object[] row = rows.get(0);
        UUID challengeId = DbValues.uuid(row[0]);
        UUID userId = DbValues.uuid(row[1]);
        int attempts = ((Number) row[2]).intValue();
        Instant expiresAt = instant(row[3]);
        Instant consumedAt = instant(row[4]);
        String username = (String) row[5];
        String status = (String) row[6];
        String encrypted = (String) row[7];
        Instant enrolledAt = instant(row[8]);
        Long lastStep = longValue(row[9]);
        Instant now = Instant.now();
        if (consumedAt != null || expiresAt == null || !expiresAt.isAfter(now)
                || !"ACTIVE".equals(status) || encrypted == null || enrolledAt == null) {
            auditService.recordAuthenticationEvent(userId, username, "MFA_CHALLENGE_INVALID", "DENIED",
                    "The challenge was expired, consumed or the account is not active.");
            throw BusinessException.unauthorized("MFA_CHALLENGE_INVALID",
                    "The verification session is invalid or has expired. Sign in again.");
        }
        if (attempts >= MAX_CHALLENGE_ATTEMPTS) {
            auditService.recordAuthenticationEvent(userId, username, "MFA_CHALLENGE_LOCKED", "DENIED",
                    "The challenge exceeded the maximum number of invalid codes.");
            throw BusinessException.unauthorized("MFA_CHALLENGE_LOCKED",
                    "Too many invalid codes. Sign in again to start a new verification session.");
        }
        String secret = decrypt(encrypted);
        OptionalLong step = TotpService.verify(secret, code, lastStep);
        String method = null;
        if (step.isPresent()) {
            method = "TOTP";
            em.createNativeQuery("""
                    UPDATE hospital.user_accounts
                       SET mfa_last_used_step = ?2, updated_at = ?3
                     WHERE id = ?1
                    """)
                    .setParameter(1, userId)
                    .setParameter(2, step.getAsLong())
                    .setParameter(3, Timestamp.from(now))
                    .executeUpdate();
        } else if (consumeRecoveryCode(userId, code, now)) {
            method = "RECOVERY_CODE";
        }
        if (method == null) {
            int nextAttempt = attempts + 1;
            em.createNativeQuery("""
                    UPDATE hospital.user_mfa_challenges
                       SET attempts = ?2
                     WHERE id = ?1
                    """)
                    .setParameter(1, challengeId)
                    .setParameter(2, nextAttempt)
                    .executeUpdate();
            auditService.recordAuthenticationEvent(userId, username, "MFA_FAILED", "FAILURE",
                    "Invalid authenticator/recovery code (attempt " + nextAttempt + " of "
                            + MAX_CHALLENGE_ATTEMPTS + ").");
            throw BusinessException.unauthorized("MFA_CODE_INVALID",
                    "The authenticator code is invalid. Check the device clock and try again.");
        }
        em.createNativeQuery("""
                UPDATE hospital.user_mfa_challenges
                   SET consumed_at = ?2
                 WHERE user_id = ?1 AND consumed_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, Timestamp.from(now))
                .executeUpdate();
        auditService.recordAuthenticationEvent(userId, username, "MFA_VERIFIED", "SUCCESS",
                "Second factor verified using " + method + ".");
        return new Verification(userId, username, method);
    }

    private boolean consumeRecoveryCode(UUID userId, String code, Instant now) {
        String normalized = normalizeRecoveryCode(code);
        if (normalized == null) return false;
        @SuppressWarnings("unchecked")
        List<?> rows = em.createNativeQuery("""
                SELECT id FROM hospital.user_mfa_recovery_codes
                 WHERE user_id = ?1 AND code_hash = ?2 AND used_at IS NULL
                """)
                .setParameter(1, userId)
                .setParameter(2, TokenService.sha256Hex(normalized))
                .getResultList();
        if (rows.isEmpty()) return false;
        Object value = rows.get(0);
        if (value instanceof Object[] row) value = row[0];
        em.createNativeQuery("""
                UPDATE hospital.user_mfa_recovery_codes
                   SET used_at = ?2
                 WHERE id = ?1 AND used_at IS NULL
                """)
                .setParameter(1, DbValues.uuid(value))
                .setParameter(2, Timestamp.from(now))
                .executeUpdate();
        auditService.recordAuthenticationEvent(userId, null, "MFA_RECOVERY_CODE_USED", "SUCCESS",
                "A one-time recovery code was consumed.");
        return true;
    }

    private String verifySecondFactor(UUID userId, String secret, Long lastStep, String code) {
        OptionalLong step = TotpService.verify(secret, code, lastStep);
        if (step.isPresent()) return "TOTP";
        if (consumeRecoveryCode(userId, code, Instant.now())) return "RECOVERY_CODE";
        return null;
    }

    private List<String> replaceRecoveryCodes(UUID userId, Instant now) {
        em.createNativeQuery("DELETE FROM hospital.user_mfa_recovery_codes WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = newRecoveryCode();
            String normalized = normalizeRecoveryCode(raw);
            em.createNativeQuery("""
                    INSERT INTO hospital.user_mfa_recovery_codes
                        (id, user_id, code_hash, created_at)
                    VALUES (?1, ?2, ?3, ?4)
                    """)
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, userId)
                    .setParameter(3, TokenService.sha256Hex(normalized))
                    .setParameter(4, Timestamp.from(now))
                    .executeUpdate();
            codes.add(raw);
        }
        return codes;
    }

    private List<Object[]> accountSecurityRow(UUID userId) {
        return em.createNativeQuery("""
                SELECT mfa_secret, mfa_enrolled_at, mfa_last_used_step
                  FROM hospital.user_accounts
                 WHERE id = ?1
                """)
                .setParameter(1, userId)
                .getResultList();
    }

    private static String newRecoveryCode() {
        byte[] bytes = new byte[7];
        RANDOM.nextBytes(bytes);
        String encoded = TotpService.encodeBase32(bytes);
        String compact = encoded.substring(0, 10);
        return compact.substring(0, 5) + "-" + compact.substring(5);
    }

    static String normalizeRecoveryCode(String code) {
        if (code == null) return null;
        String compact = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return compact.length() == 10 ? compact : null;
    }

    private static String decrypt(String encrypted) {
        try {
            return SecretCipher.decrypt(encrypted);
        } catch (IllegalStateException ex) {
            throw new BusinessException(500, "MFA_CONFIGURATION_ERROR",
                    "The MFA secret cannot be decrypted. Contact the security administrator.");
        }
    }

    private static Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return null;
    }

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
