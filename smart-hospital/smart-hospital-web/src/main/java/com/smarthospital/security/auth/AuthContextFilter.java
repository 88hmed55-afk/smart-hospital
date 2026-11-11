package com.smarthospital.security.auth;

import com.smarthospital.security.domain.CurrentUser;
import jakarta.annotation.Priority;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bearer-token authentication and declarative role enforcement.
 *
 * <p>The filter runs after JAX-RS resource matching, so it can enforce
 * {@link RequiresRoles} itself instead of relying on container policy mapping.
 * Requests to the token endpoint are the only anonymous API surface.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthContextFilter implements ContainerRequestFilter {
    private static final Set<String> ANONYMOUS_PATHS =
            Set.of("auth/login", "auth/mfa/verify");
    private static final Set<String> PASSWORD_ROTATION_ALLOWED =
            Set.of("security/me", "auth/password", "auth/logout");
    private static final Set<String> MFA_ENROLLMENT_ALLOWED =
            Set.of("security/me", "auth/mfa/enroll/start", "auth/mfa/enroll/confirm", "auth/logout");

    @Inject
    private AuthService authService;

    @Inject
    private AuthenticatedUserHolder holder;

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        while (path.startsWith("/")) path = path.substring(1);
        if (ANONYMOUS_PATHS.contains(path)) {
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        String authorization = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        String token = bearerToken(authorization);
        if (token == null) {
            abort(request, 401, "AUTHENTICATION_REQUIRED", "A valid bearer token is required.");
            return;
        }

        CurrentUser user = authService.validate(token);
        if (user == null) {
            abort(request, 401, "SESSION_INVALID", "The session is missing, expired or revoked.");
            return;
        }

        holder.set(user);
        request.setProperty("smart-hospital.raw-token", token);
        request.setSecurityContext(new HospitalSecurityContext(user, request));

        if (user.mustChangePassword() && SecuritySettings.enforcePasswordRotation()
                && !isAllowedDuringRotation(path)) {
            abort(request, 403, "PASSWORD_CHANGE_REQUIRED",
                    "You must change your password before using the application.");
            return;
        }

        if (user.mfaEnrollmentRequired() && !isAllowedDuringMfaEnrollment(path)) {
            abort(request, 403, "MFA_ENROLLMENT_REQUIRED",
                    "Your role requires multi-factor authentication. Complete enrolment before using the application.");
            return;
        }

        String denial = checkRoles(user);
        if (denial != null) {
            abort(request, 403, "FORBIDDEN", denial);
        }
    }

    /** Paths a signed-in account may still use while a password rotation is pending. */
    static boolean isAllowedDuringRotation(String path) {
        return inAllowlist(path, PASSWORD_ROTATION_ALLOWED);
    }

    /** Paths a signed-in account may still use while MFA enrolment is mandatory. */
    static boolean isAllowedDuringMfaEnrollment(String path) {
        return inAllowlist(path, MFA_ENROLLMENT_ALLOWED);
    }

    private static boolean inAllowlist(String path, Set<String> allowlist) {
        if (path == null) return false;
        String normalized = path;
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return allowlist.contains(normalized);
    }

    private String checkRoles(CurrentUser user) {
        if (resourceInfo == null) return null;
        Method method = resourceInfo.getResourceMethod();
        Class<?> type = resourceInfo.getResourceClass();
        if (method != null && method.isAnnotationPresent(PermitAll.class)) return null;
        RequiresRoles required = method == null ? null : method.getAnnotation(RequiresRoles.class);
        if (required == null && type != null) required = type.getAnnotation(RequiresRoles.class);
        if (required == null) return null;
        Set<String> held = Set.copyOf(user.roleCodes());
        for (String role : required.value()) {
            if (held.contains(role)) return null;
        }
        return "This operation requires a role that is not assigned to your account.";
    }

    private static void abort(ContainerRequestContext request, int status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("code", code);
        body.put("title", status == 401 ? "Unauthorized" : "Forbidden");
        body.put("detail", message);
        Response response = Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"smart-hospital\"")
                .build();
        request.abortWith(response);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        if (value.length() > 512) return null;
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = value.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /** Minimal SecurityContext backed by the hospital role model. */
    private static final class HospitalSecurityContext implements SecurityContext {
        private final CurrentUser user;
        private final ContainerRequestContext request;
        private final Principal principal;
        private final boolean secure;

        HospitalSecurityContext(CurrentUser user, ContainerRequestContext request) {
            this.user = user;
            this.request = request;
            this.principal = () -> user.username();
            String scheme = request.getUriInfo().getRequestUri().getScheme();
            this.secure = "https".equalsIgnoreCase(scheme);
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public boolean isUserInRole(String role) {
            if (role == null) return false;
            return user.roleCodes().contains(role.toUpperCase(Locale.ROOT));
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        @Override
        public String getAuthenticationScheme() {
            return "Bearer";
        }
    }
}
