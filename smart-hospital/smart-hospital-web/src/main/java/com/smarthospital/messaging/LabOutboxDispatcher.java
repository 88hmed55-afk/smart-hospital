package com.smarthospital.messaging;

import com.smarthospital.common.persistence.DbValues;

import jakarta.annotation.Resource;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishes committed laboratory events from the transactional outbox.
 * When the broker is unavailable, the transaction rolls back and pending events
 * remain in the database for a later scheduled attempt.
 */
@Singleton
@Startup
public class LabOutboxDispatcher {
    private static final Logger LOG = Logger.getLogger(LabOutboxDispatcher.class.getName());

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @Inject
    @JMSConnectionFactory("java:app/jms/SmartHospitalConnectionFactory")
    private JMSContext jmsContext;

    @Resource(lookup = "java:app/jms/SmartHospitalLabQueue")
    private Queue labQueue;

    @Schedule(second = "0,20,40", minute = "*", hour = "*", persistent = false)
    @Lock(LockType.WRITE)
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void publishPendingLaboratoryEvents() {
        @SuppressWarnings("unchecked")
        List<Object[]> pending = em.createNativeQuery("""
                SELECT id, event_type, aggregate_id
                  FROM hospital.message_outbox
                 WHERE delivery_status='PENDING'
                   AND event_type IN ('LAB_ORDER_CREATED','LAB_RESULT_RELEASED')
                   AND next_attempt_at <= now()
                 ORDER BY created_at
                 LIMIT 25
                 FOR UPDATE SKIP LOCKED
                """)
                .getResultList();

        for (Object[] row : pending) {
            UUID eventId = DbValues.uuid(row[0]);
            String eventType = (String) row[1];
            UUID aggregateId = DbValues.uuid(row[2]);
            try {
                String message = eventId + "|" + eventType + "|" + aggregateId;
                jmsContext.createProducer().send(labQueue, message);
                em.createNativeQuery("""
                        UPDATE hospital.message_outbox
                           SET delivery_status='PUBLISHED',
                               attempt_count=attempt_count+1,
                               published_at=now(),
                               last_error_code=NULL
                         WHERE id=?1
                        """)
                        .setParameter(1, eventId)
                        .executeUpdate();
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Laboratory outbox publication failed; transaction will roll back and retry.", exception);
                throw exception;
            }
        }
    }
}
