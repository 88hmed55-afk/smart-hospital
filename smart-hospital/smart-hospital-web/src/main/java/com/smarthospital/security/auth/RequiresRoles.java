package com.smarthospital.security.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative role requirement enforced by {@link AuthContextFilter}.
 *
 * <p>This deliberately replaces {@code jakarta.annotation.security.RolesAllowed}.
 * GlassFish ships {@code org.omnifaces.jwt} inside its MicroProfile JWT module and
 * registers a dynamic feature for every {@code @RolesAllowed} resource; that feature
 * tries to authenticate the request through the servlet container (BASIC) and
 * returns a container 401 before the application bearer filter can run. Hospital
 * roles are resolved from the application database, not from the container realm,
 * so the application owns this annotation.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresRoles {
    /** Any one of these hospital role codes grants access. */
    String[] value();
}
