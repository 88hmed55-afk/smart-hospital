package com.smarthospital.governance.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.common.api.PageResponse;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.governance.dto.NotificationResponse;
import com.smarthospital.governance.dto.UnreadCountResponse;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Stateless
public class NotificationInboxService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;
    @EJB private CurrentUserService currentUserService;
    public PageResponse<NotificationResponse> list(boolean unreadOnly, int requestedOffset, int requestedLimit) {
        CurrentUser user = currentUserService.requireCurrentUser();
        int offset = Math.max(0, Math.min(requestedOffset, 1_000_000));
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        String where = " WHERE recipient_user_id=?1 " + (unreadOnly ? " AND read_at IS NULL " : "");
        long total = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.notifications " + where)
                .setParameter(1, user.userId()).getSingleResult()).longValue();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, notification_type, title, body, reference_type, reference_id, created_at, read_at
                  FROM hospital.notifications
                """ + where + " ORDER BY created_at DESC, id DESC")
                .setParameter(1, user.userId())
                .setFirstResult(offset).setMaxResults(limit).getResultList();
        List<NotificationResponse> items = new ArrayList<>();
        for (Object[] row : rows) {
            NotificationResponse dto = new NotificationResponse();
            dto.id = DbValues.uuid(row[0]);
            dto.notificationType = (String) row[1];
            dto.title = (String) row[2];
            dto.body = (String) row[3];
            dto.referenceType = (String) row[4];
            dto.referenceId = DbValues.uuid(row[5]);
            dto.createdAt = toInstant(row[6]);
            dto.readAt = toInstant(row[7]);
            items.add(dto);
        }
        return new PageResponse<>(items, total, offset, limit);
    }
    public UnreadCountResponse unreadCount() {
        UUID userId = currentUserService.requireCurrentUser().userId();
        long count = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.notifications WHERE recipient_user_id=?1 AND read_at IS NULL")
                .setParameter(1, userId).getSingleResult()).longValue();
        return new UnreadCountResponse(count, 0);
    }
    public UnreadCountResponse markRead(UUID notificationId) {
        UUID userId = currentUserService.requireCurrentUser().userId();
        int updated = em.createNativeQuery("""
                UPDATE hospital.notifications SET read_at=now()
                 WHERE id=?1 AND recipient_user_id=?2 AND read_at IS NULL
                """)
                .setParameter(1, notificationId)
                .setParameter(2, userId)
                .executeUpdate();
        if (updated == 0) {
            Long owned = ((Number) em.createNativeQuery("SELECT count(*) FROM hospital.notifications WHERE id=?1 AND recipient_user_id=?2")
                    .setParameter(1, notificationId).setParameter(2, userId).getSingleResult()).longValue();
            if (owned == 0) throw BusinessException.notFound("Notification");
        }
        return unreadCount();
    }
    public UnreadCountResponse markAllRead() {
        UUID userId = currentUserService.requireCurrentUser().userId();
        int updated = em.createNativeQuery("UPDATE hospital.notifications SET read_at=now() WHERE recipient_user_id=?1 AND read_at IS NULL")
                .setParameter(1, userId).executeUpdate();
        return new UnreadCountResponse(0, updated);
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return null;
    }
}
