package com.smarthospital.config;

import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/**
 * Baseline HTTP hardening for every response: transport security, clickjacking
 * protection, MIME sniffing protection, a restrictive Content Security Policy,
 * per-request audit metadata and an optional HTTPS redirect.
 *
 * <p>Enable the redirect in production with {@code -Dsmarthospital.forceHttps=true}
 * or the environment variable {@code SMART_HOSPITAL_FORCE_HTTPS=true}.</p>
 */
@WebFilter(urlPatterns = "/*", dispatcherTypes = {
        DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC
})
public class SecurityHeadersFilter implements Filter {
    private static final String CSP = "default-src 'self'; "
            + "base-uri 'self'; "
            + "frame-ancestors 'none'; "
            + "object-src 'none'; "
            + "img-src 'self' data: blob:; "
            + "font-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; "
            + "script-src 'self'; "
            + "connect-src 'self'; "
            + "form-action 'self';";

    @Inject
    private RequestMetadata requestMetadata;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (requestMetadata != null) {
            requestMetadata.setClientIp(clientIp(httpRequest));
            requestMetadata.setUserAgent(truncate(httpRequest.getHeader("User-Agent"), 400));
        }

        boolean secure = httpRequest.isSecure()
                || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"));
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Referrer-Policy", "no-referrer");
        httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        httpResponse.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        httpResponse.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        httpResponse.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        httpResponse.setHeader("Content-Security-Policy", CSP);
        if (secure) {
            httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        if (path.startsWith("/api/")) {
            httpResponse.setHeader("Cache-Control", "no-store");
            httpResponse.setHeader("Pragma", "no-cache");
        }

        if (forceHttps() && !secure && !httpRequest.getMethod().equalsIgnoreCase("OPTIONS")) {
            String target = "https://" + httpRequest.getServerName()
                    + (httpRequest.getServerPort() == 8080 ? ":8181" : "")
                    + path
                    + (httpRequest.getQueryString() == null ? "" : "?" + httpRequest.getQueryString());
            httpResponse.setStatus(308);
            httpResponse.setHeader("Location", target);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean forceHttps() {
        String property = System.getProperty("smarthospital.forceHttps");
        if (property != null) return Boolean.parseBoolean(property);
        String env = System.getenv("SMART_HOSPITAL_FORCE_HTTPS");
        return env != null && Boolean.parseBoolean(env);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return truncate(first, 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\r\\n]", " ");
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
