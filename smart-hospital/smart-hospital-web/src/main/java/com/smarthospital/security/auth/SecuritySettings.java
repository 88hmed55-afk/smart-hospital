package com.smarthospital.security.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deployment-level security switches resolved from JVM options or environment
 * variables. Keeping them in one place avoids drift between the request filter,
 * the metrics endpoint and the documentation.
 */
public final class SecuritySettings {

    private SecuritySettings() { }

    /**
     * When enabled, an account with {@code must_change_password = TRUE} may only call
     * {@code /security/me}, {@code /auth/password} and {@code /auth/logout}. Enable this
     * in production after provisioning accounts; leave it off only for local demos.
     */
    public static boolean enforcePasswordRotation() {
        return bool("smarthospital.security.enforcePasswordRotation",
                "SMART_HOSPITAL_ENFORCE_PASSWORD_ROTATION", false);
    }

    /** Redirects plain HTTP requests to HTTPS when enabled (default off). */
    public static boolean forceHttps() {
        return bool("smarthospital.forceHttps", "SMART_HOSPITAL_FORCE_HTTPS", false);
    }

    /**
     * Roles that must use TOTP MFA. Empty by default: MFA is available to every
     * account and can be enrolled voluntarily, but enrolment is only forced when
     * this list is non-empty. Production should set at least the privileged roles,
     * for example {@code HOSPITAL_ADMIN,HOSPITAL_MANAGER,DOCTOR,PHARMACIST}.
     */
    public static Set<String> mfaRequiredRoles() {
        String raw = propertyOrEnvironment("smarthospital.mfa.requiredRoles",
                "SMART_HOSPITAL_MFA_REQUIRED_ROLES");
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean mfaRequiredFor(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) return false;
        Set<String> required = mfaRequiredRoles();
        if (required.isEmpty()) return false;
        return roleCodes.stream()
                .map(code -> code.toUpperCase(Locale.ROOT))
                .anyMatch(required::contains);
    }

    /** True when at least one role is configured, which is also when enrolment is enforced. */
    public static boolean mfaEnrollmentEnforced() {
        return !mfaRequiredRoles().isEmpty();
    }

    public static String mfaIssuer() {
        String value = propertyOrEnvironment("smarthospital.mfa.issuer", "SMART_HOSPITAL_MFA_ISSUER");
        return value == null || value.isBlank() ? "Smart Hospital" : value.trim();
    }

    private static boolean bool(String property, String environment, boolean fallback) {
        String value = propertyOrEnvironment(property, environment);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String propertyOrEnvironment(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value;
    }
}
