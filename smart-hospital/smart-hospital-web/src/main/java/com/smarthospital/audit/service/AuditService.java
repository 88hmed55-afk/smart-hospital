package com.smarthospital.audit.service;

import com.smarthospital.config.RequestMetadata;
import com.smarthospital.security.auth.TokenService;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes tamper-evident audit records in the same JTA transaction as the protected
 * operation (security events use an independent transaction so failed logins are
 * still recorded after the business transaction rolls back).
 *
 * <p>Every row carries {@code prev_hash} and {@code row_hash}; the hash chain head
 * is serialised through {@code audit_chain_state} so concurrent writers cannot fork
 * the chain. Clinical text and contact values are deliberately excluded.</p>
 */
@Stateless
public class AuditService {
    private static final String GENESIS = "0".repeat(64);

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @jakarta.ejb.EJB
    private CurrentUserService currentUserService;

    @Inject
    private RequestMetadata requestMetadata;

    public void recordAction(String actionCode, String resourceType, UUID resourceId, String context) {
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        recordAction(actionCode, resourceType, resourceId, context, currentUser.defaultBranchId());
    }

    public void recordAction(String actionCode, String resourceType, UUID resourceId, String context, UUID branchId) {
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        UUID effectiveBranchId = branchId == null ? currentUser.defaultBranchId() : branchId;
        append(currentUser.userId(), effectiveBranchId, actionCode, resourceType, resourceId, "SUCCESS",
                metadata(currentUser.username(), context));
    }

    /** Records a genuinely global action with a null branch, rather than attributing it to the default branch. */
    public void recordGlobalAction(String actionCode, String resourceType, UUID resourceId, String context) {
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        append(currentUser.userId(), null, actionCode, resourceType, resourceId, "SUCCESS",
                metadata(currentUser.username(), context));
    }

    /**
     * Records an authentication event even when the surrounding login transaction
     * rolls back. Unknown usernames are recorded with a null actor and the attempted
     * principal in metadata.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void recordAuthenticationEvent(UUID actorUserId, String username, String actionCode,
                                          String outcome, String context) {
        append(actorUserId, null, actionCode, "USER_ACCOUNT", actorUserId, outcome,
                metadata(username, context));
    }

    /**
     * Records an event in an independent transaction so it survives a business
     * rollback (for example a blocked clinical action or a safety override).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void recordIndependentAction(String actionCode, String resourceType, UUID resourceId,
                                        String outcome, String context, UUID branchId) {
        CurrentUser currentUser = currentUserService.requireCurrentUser();
        append(currentUser.userId(), branchId, actionCode, resourceType, resourceId, outcome,
                metadata(currentUser.username(), context));
    }

    /**
     * Records an automated platform action (maintenance, scheduled jobs). There is no
     * authenticated caller, so the actor is null and the principal is {@code system}.
     * The event is still chained like any other audit row.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void recordSystemAction(String actionCode, String resourceType, String outcome, String context) {
        append(null, null, actionCode, resourceType, null, outcome, metadata("system", context));
    }

    private void append(UUID actorUserId, UUID branchId, String actionCode, String resourceType,
                        UUID resourceId, String outcome, String metadataJson) {
        List<?> state = em.createNativeQuery(
                        "SELECT last_sequence, last_hash FROM hospital.audit_chain_state WHERE id = 1 FOR UPDATE")
                .getResultList();
        String previousHash = GENESIS;
        long nextSequence = 1L;
        if (!state.isEmpty()) {
            Object[] row = (Object[]) state.get(0);
            nextSequence = ((Number) row[0]).longValue() + 1;
            String stored = (String) row[1];
            if (stored != null && !stored.isBlank()) previousHash = stored;
        }

        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String payload = String.join("|",
                previousHash,
                id.toString(),
                value(actorUserId),
                value(branchId),
                actionCode,
                resourceType,
                value(resourceId),
                outcome,
                occurredAt.toString(),
                metadataJson);
        String rowHash = TokenService.sha256Hex(payload);

        em.createNativeQuery("""
                INSERT INTO hospital.audit_logs
                    (id, actor_user_id, branch_id, action_code, resource_type, resource_id,
                     outcome, ip_address, user_agent, metadata, prev_hash, row_hash, hash_payload, occurred_at)
                VALUES
                    (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, CAST(?10 AS JSON), ?11, ?12, ?13, ?14)
                """)
                .setParameter(1, id)
                .setParameter(2, actorUserId)
                .setParameter(3, branchId)
                .setParameter(4, actionCode)
                .setParameter(5, resourceType)
                .setParameter(6, resourceId)
                .setParameter(7, outcome)
                .setParameter(8, requestMetadata == null ? null : truncate(requestMetadata.getClientIp(), 45))
                .setParameter(9, requestMetadata == null ? null : truncate(requestMetadata.getUserAgent(), 400))
                .setParameter(10, metadataJson)
                .setParameter(11, previousHash)
                .setParameter(12, rowHash)
                .setParameter(13, payload)
                .setParameter(14, Timestamp.from(occurredAt))
                .executeUpdate();

        if (state.isEmpty()) {
            em.createNativeQuery("""
                    INSERT INTO hospital.audit_chain_state (id, last_sequence, last_hash, updated_at)
                    VALUES (1, ?1, ?2, ?3)
                    """)
                    .setParameter(1, nextSequence)
                    .setParameter(2, rowHash)
                    .setParameter(3, Timestamp.from(occurredAt))
                    .executeUpdate();
        } else {
            em.createNativeQuery("""
                    UPDATE hospital.audit_chain_state
                       SET last_sequence = ?1, last_hash = ?2, updated_at = ?3
                     WHERE id = 1
                    """)
                    .setParameter(1, nextSequence)
                    .setParameter(2, rowHash)
                    .setParameter(3, Timestamp.from(occurredAt))
                    .executeUpdate();
        }
    }

    private static String metadata(String principal, String context) {
        return "{\"principal\":" + json(principal) + ",\"context\":" + json(context == null ? "" : context) + "}";
    }

    private static String json(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
