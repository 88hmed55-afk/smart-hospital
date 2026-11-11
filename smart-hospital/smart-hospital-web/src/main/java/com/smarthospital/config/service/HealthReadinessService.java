package com.smarthospital.config.service;

import com.smarthospital.config.dto.HealthReadinessResponse;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

/** Lightweight, read-only readiness probes for the configured database and JMS broker. */
@Stateless
public class HealthReadinessService {
    @Resource(lookup = "jdbc/SmartHospitalDS")
    private DataSource dataSource;

    @Resource(lookup = "java:app/jms/SmartHospitalConnectionFactory")
    private ConnectionFactory connectionFactory;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public HealthReadinessResponse check() {
        HealthReadinessResponse response = new HealthReadinessResponse();
        response.application = "smart-hospital";
        response.timestamp = Instant.now();

        boolean databaseUp = checkDatabase();
        response.checks.put("database", databaseUp ? "UP" : "DOWN");

        boolean messagingUp = checkMessaging();
        response.checks.put("messaging", messagingUp ? "UP" : "DOWN");

        response.status = databaseUp && messagingUp ? "UP" : "DEGRADED";
        return response;
    }

    private boolean checkDatabase() {
        if (dataSource == null) return false;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (SQLException | RuntimeException exception) {
            return false;
        }
    }

    private boolean checkMessaging() {
        if (connectionFactory == null) return false;
        try (JMSContext context = connectionFactory.createContext()) {
            context.start();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
