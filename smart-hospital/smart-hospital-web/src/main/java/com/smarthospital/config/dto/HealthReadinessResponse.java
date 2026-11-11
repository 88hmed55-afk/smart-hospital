package com.smarthospital.config.dto;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sanitized dependency-readiness result; never includes connection strings or exception text. */
public class HealthReadinessResponse {
    public String status;
    public String application;
    public Instant timestamp;
    public Map<String, String> checks = new LinkedHashMap<>();

    public HealthReadinessResponse() { }
}
