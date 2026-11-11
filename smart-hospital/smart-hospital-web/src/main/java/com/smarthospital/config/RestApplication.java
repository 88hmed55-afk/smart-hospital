package com.smarthospital.config;

import jakarta.jms.JMSConnectionFactoryDefinition;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashMap;
import java.util.Map;

/**
 * JAX-RS entry point.
 *
 * <p>Provider scanning is deliberately restricted to {@code com.smarthospital}.
 * GlassFish ships {@code org.omnifaces.jwt} inside its {@code microprofile-jwt-auth}
 * module and Jersey would otherwise auto-discover
 * {@code RolesAllowedRequestFilter}, which tries to perform container BASIC
 * authentication for every {@code @RolesAllowed} resource and returns a container
 * 401 before the application bearer filter runs. Role enforcement is performed by
 * {@link com.smarthospital.security.auth.AuthContextFilter} instead.</p>
 */
@ApplicationPath("/api/v1")
@JMSConnectionFactoryDefinition(
        name = "java:app/jms/SmartHospitalConnectionFactory",
        interfaceName = "jakarta.jms.ConnectionFactory")
@JMSDestinationDefinition(
        name = "java:app/jms/SmartHospitalLabQueue",
        interfaceName = "jakarta.jms.Queue",
        destinationName = "SmartHospitalLabQueue")
public class RestApplication extends Application {

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jersey.config.server.provider.packages", "com.smarthospital");
        properties.put("jersey.config.server.provider.scanning.recursive", true);
        return properties;
    }
}
