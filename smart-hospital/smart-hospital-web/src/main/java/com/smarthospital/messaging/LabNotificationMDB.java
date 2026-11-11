package com.smarthospital.messaging;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;

/** Delivers laboratory workflow events to the correct staff notification inboxes. */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(
                propertyName = "destinationLookup",
                propertyValue = "java:app/jms/SmartHospitalLabQueue"),
        @ActivationConfigProperty(
                propertyName = "destinationType",
                propertyValue = "jakarta.jms.Queue")
})
public class LabNotificationMDB implements MessageListener {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @Override
    public void onMessage(Message message) {
        if (!(message instanceof TextMessage textMessage)) {
            throw new IllegalArgumentException("Laboratory event queue accepts TextMessage payloads only.");
        }
        try {
            String[] parts = textMessage.getText().split("\\|", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid laboratory event message format.");
            }
            UUID eventId = UUID.fromString(parts[0]);
            String eventType = parts[1];
            UUID aggregateId = UUID.fromString(parts[2]);

            switch (eventType) {
                case "LAB_ORDER_CREATED" -> notifyLaboratoryTeam(eventId, aggregateId);
                case "LAB_RESULT_RELEASED" -> notifyOrderingDoctor(eventId, aggregateId);
                default -> throw new IllegalArgumentException("Unsupported laboratory event type: " + eventType);
            }
        } catch (JMSException exception) {
            throw new IllegalStateException("Could not read the laboratory event message.", exception);
        }
    }

    private void notifyLaboratoryTeam(UUID eventId, UUID labOrderId) {
        em.createNativeQuery("""
                INSERT IGNORE INTO hospital.notifications
                    (source_event_id, recipient_user_id, notification_type, title, body, reference_type, reference_id)
                SELECT ?1, ua.id, 'LAB_ORDER_ASSIGNED', 'New laboratory order',
                       'A new laboratory order is ready for processing.', 'LAB_ORDER', lo.id
                  FROM hospital.lab_orders lo
                  JOIN hospital.encounters e ON e.id=lo.encounter_id
                  JOIN hospital.patient_branch_access pba ON pba.patient_id=e.patient_id AND pba.branch_id=e.branch_id AND pba.revoked_at IS NULL
                  JOIN hospital.staff_members sm ON sm.branch_id=e.branch_id AND sm.employment_status='ACTIVE'
                  JOIN hospital.departments d ON d.id=sm.department_id AND d.department_type='LABORATORY' AND d.is_active=TRUE
                  JOIN hospital.user_accounts ua ON ua.staff_id=sm.id AND ua.account_status='ACTIVE'
                  JOIN hospital.user_roles ur ON ur.user_id=ua.id
                  JOIN hospital.roles r ON r.id=ur.role_id AND r.code IN ('LAB_TECHNICIAN','LAB_SUPERVISOR')
                  JOIN hospital.user_branch_access uba ON uba.user_id=ua.id AND uba.branch_id=e.branch_id
                 WHERE lo.id=?2
                """)
                .setParameter(1, eventId)
                .setParameter(2, labOrderId)
                .executeUpdate();
    }

    private void notifyOrderingDoctor(UUID eventId, UUID resultId) {
        em.createNativeQuery("""
                INSERT IGNORE INTO hospital.notifications
                    (source_event_id, recipient_user_id, notification_type, title, body, reference_type, reference_id)
                SELECT ?1, ua.id, 'LAB_RESULT_READY', 'Laboratory result released',
                       'A laboratory result has been reviewed and released for your encounter.', 'LAB_RESULT', lr.id
                  FROM hospital.lab_results lr
                  JOIN hospital.lab_order_items loi ON loi.id=lr.lab_order_item_id
                  JOIN hospital.lab_orders lo ON lo.id=loi.lab_order_id
                  JOIN hospital.encounters e ON e.id=lo.encounter_id
                  JOIN hospital.patient_branch_access pba ON pba.patient_id=e.patient_id AND pba.branch_id=e.branch_id AND pba.revoked_at IS NULL
                  JOIN hospital.user_accounts ua ON ua.staff_id=lo.ordered_by AND ua.account_status='ACTIVE'
                  JOIN hospital.user_branch_access uba ON uba.user_id=ua.id AND uba.branch_id=e.branch_id
                 WHERE lr.id=?2 AND lr.released_at IS NOT NULL
                """)
                .setParameter(1, eventId)
                .setParameter(2, resultId)
                .executeUpdate();
    }
}
