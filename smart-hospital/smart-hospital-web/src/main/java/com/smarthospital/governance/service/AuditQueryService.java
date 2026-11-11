package com.smarthospital.governance.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.common.api.PageResponse;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.governance.dto.AuditLogResponse;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Stateless
public class AuditQueryService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;
    @EJB private CurrentUserService currentUserService;
    public PageResponse<AuditLogResponse> search(UUID requestedBranchId, String fromText, String toText,
                                                  String actionCode, String resourceType,
                                                  int requestedOffset, int requestedLimit) {
        CurrentUser user = currentUserService.requireCurrentUser();
        UUID branchId = requestedBranchId == null ? user.defaultBranchId() : requestedBranchId;
        currentUserService.requireBranchAccess(branchId);

        Instant from = parseInstant(fromText, "from");
        Instant to = parseInstant(toText, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("INVALID_AUDIT_RANGE", "The from timestamp must be before the to timestamp.");
        }
        int offset = Math.max(0, Math.min(requestedOffset, 1_000_000));
        int limit = Math.max(1, Math.min(requestedLimit, 100));

        StringBuilder where = new StringBuilder(user.roleCodes().contains("HOSPITAL_ADMIN")
                ? " WHERE (al.branch_id = ?1 OR al.branch_id IS NULL) "
                : " WHERE al.branch_id = ?1 ");
        List<Object> parameters = new ArrayList<>();
        parameters.add(branchId);
        int nextIndex = 2;
        if (from != null) {
            where.append(" AND al.occurred_at >= ?").append(nextIndex++).append(' ');
            parameters.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND al.occurred_at <= ?").append(nextIndex++).append(' ');
            parameters.add(Timestamp.from(to));
        }
        if (actionCode != null && !actionCode.isBlank()) {
            where.append(" AND LOWER(al.action_code) LIKE LOWER(?").append(nextIndex++).append(") ");
            parameters.add("%" + actionCode.trim() + "%");
        }
        if (resourceType != null && !resourceType.isBlank()) {
            where.append(" AND al.resource_type = ?").append(nextIndex++).append(' ');
            parameters.add(resourceType.trim().toUpperCase());
        }

        var countQuery = em.createNativeQuery("SELECT count(*) FROM hospital.audit_logs al " + where);
        var dataQuery = em.createNativeQuery("""
                SELECT al.id, COALESCE(ua.username, '[account unavailable]'), al.branch_id,
                       COALESCE(hb.name, CASE WHEN al.branch_id IS NULL THEN 'عام لكل الفروع' END),
                       al.action_code, al.resource_type, al.resource_id,
                       al.outcome, al.occurred_at
                  FROM hospital.audit_logs al
                  LEFT JOIN hospital.user_accounts ua ON ua.id=al.actor_user_id
                  LEFT JOIN hospital.hospital_branches hb ON hb.id=al.branch_id
                """ + where + " ORDER BY al.occurred_at DESC, al.id DESC");
        bindFilters(countQuery, parameters);
        bindFilters(dataQuery, parameters);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.setFirstResult(offset).setMaxResults(limit).getResultList();
        List<AuditLogResponse> items = new ArrayList<>();
        for (Object[] row : rows) {
            AuditLogResponse dto = new AuditLogResponse();
            dto.id = DbValues.uuid(row[0]);
            dto.actorUsername = (String) row[1];
            dto.branchId = DbValues.uuid(row[2]);
            dto.branchName = (String) row[3];
            dto.actionCode = (String) row[4];
            dto.resourceType = (String) row[5];
            dto.resourceId = DbValues.uuid(row[6]);
            dto.outcome = (String) row[7];
            dto.occurredAt = toInstant(row[8]);
            items.add(dto);
        }
        return new PageResponse<>(items, total, offset, limit);
    }

    private void bindFilters(jakarta.persistence.Query query, List<Object> parameters) {
        for (int index = 0; index < parameters.size(); index++) {
            query.setParameter(index + 1, parameters.get(index));
        }
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException exception) {
            throw BusinessException.badRequest("INVALID_TIMESTAMP", "The " + field + " timestamp must use ISO-8601 UTC format.");
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }
}
