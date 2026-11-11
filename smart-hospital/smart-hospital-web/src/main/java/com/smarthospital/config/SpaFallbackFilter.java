package com.smarthospital.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Serves the React single-page application for client-side routes.
 *
 * <p>The UI uses the HTML5 history API (for example {@code /dashboard} or
 * {@code /patients}). Those paths do not exist as files, so without a fallback the
 * container would answer 404. Requests for the API or for real static assets are
 * passed through unchanged.</p>
 */
@WebFilter(urlPatterns = "/*")
public class SpaFallbackFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        boolean passthrough = path.equals("/")
                || path.startsWith("/api/")
                || path.startsWith("/assets/")
                || path.contains(".");
        if (passthrough) {
            chain.doFilter(request, response);
            return;
        }
        httpRequest.getRequestDispatcher("/index.html").forward(request, response);
    }
}
