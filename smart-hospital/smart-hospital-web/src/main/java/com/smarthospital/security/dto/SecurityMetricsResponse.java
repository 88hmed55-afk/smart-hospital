package com.smarthospital.security.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate security posture for operations dashboards. Contains no patient data
 * and no credentials, only counts and timestamps safe for a hospital administrator
 * or security operations centre.
 */
public class SecurityMetricsResponse {
    public Instant generatedAt;
    public long activeSessions;
    public long lockedAccounts;
    public long failedLogins24h;
    public long successfulLogins24h;
    public long accountsPendingPasswordChange;
    public Instant lastSuccessfulLoginAt;
    public boolean auditChainIntact;
    public long auditChainRows;
    public long auditHashMismatches;
    public boolean httpsRequest;
    public boolean forceHttpsEnabled;
    public boolean passwordRotationEnforced;
    public boolean mfaEnrollmentEnforced;
    public List<String> mfaRequiredRoles;
    public long mfaEnabledAccounts;
    public long accountsPendingMfaEnrollment;
    public long mfaChallengesIssued24h;
    public long mfaVerifications24h;
    public long mfaFailures24h;
}
