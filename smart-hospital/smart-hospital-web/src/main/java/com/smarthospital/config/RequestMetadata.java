package com.smarthospital.config;

import jakarta.enterprise.context.RequestScoped;

/** Per-request network metadata captured by the security filter and used by audit logging. */
@RequestScoped
public class RequestMetadata {
    private String clientIp;
    private String userAgent;

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
