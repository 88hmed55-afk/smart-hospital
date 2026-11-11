package com.smarthospital.security.dto;

import java.time.Instant;

/** Outcome of one authentication-state maintenance run. Contains no PHI. */
public class MaintenanceResult {
    public Instant executedAt;
    public boolean enabled;
    public int retentionDays;
    public int prunedSessions;
    public int prunedMfaChallenges;
    public int releasedLockouts;
}
