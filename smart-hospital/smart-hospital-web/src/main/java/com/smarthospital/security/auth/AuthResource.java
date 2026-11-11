package com.smarthospital.security.auth;

import com.smarthospital.common.error.BusinessException;
import com.smarthospital.security.auth.dto.ChangePasswordRequest;
import com.smarthospital.security.auth.dto.LoginRequest;
import com.smarthospital.security.auth.dto.LoginResponse;
import com.smarthospital.security.mfa.MfaService;
import com.smarthospital.security.mfa.dto.MfaCodeRequest;
import com.smarthospital.security.mfa.dto.MfaDisableRequest;
import com.smarthospital.security.mfa.dto.MfaEnrollmentConfirmResponse;
import com.smarthospital.security.mfa.dto.MfaEnrollmentStartResponse;
import com.smarthospital.security.mfa.dto.MfaRecoveryCodesResponse;
import com.smarthospital.security.mfa.dto.MfaStatusResponse;
import com.smarthospital.security.mfa.dto.MfaVerifyRequest;
import com.smarthospital.security.dto.CurrentSessionResponse;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;

/** Token endpoint, MFA lifecycle, logout and self-service password rotation. */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @EJB
    private AuthService authService;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("CREDENTIALS_REQUIRED", "Username and password are required.");
        }
        return toResponse(authService.login(request.username, request.password));
    }

    /** Second login step. Anonymous by design: the challenge token is not a session. */
    @POST
    @Path("/mfa/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginResponse verifyMfa(MfaVerifyRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("MFA_CODE_REQUIRED", "The challenge token and code are required.");
        }
        return toResponse(authService.verifyMfa(request.challengeToken, request.code));
    }

    @POST
    @Path("/logout")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public Response logout(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(bearerToken(authorization));
        return Response.noContent().build();
    }

    @POST
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public Response changePassword(ChangePasswordRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("PASSWORD_REQUIRED", "Current and new passwords are required.");
        }
        authService.changePassword(request.currentPassword, request.newPassword);
        return Response.noContent().build();
    }

    @GET
    @Path("/mfa/status")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public MfaStatusResponse mfaStatus() {
        return MfaStatusResponse.from(authService.mfaStatus());
    }

    /** Generates a new pending TOTP secret. It does not activate MFA until confirm succeeds. */
    @POST
    @Path("/mfa/enroll/start")
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public MfaEnrollmentStartResponse startEnrollment() {
        MfaService.EnrollmentStart start = authService.beginMfaEnrollment();
        MfaEnrollmentStartResponse response = new MfaEnrollmentStartResponse();
        response.secret = start.secret();
        response.otpauthUri = start.otpauthUri();
        response.issuer = start.issuer();
        response.account = start.account();
        response.pendingSince = Instant.now();
        return response;
    }

    @POST
    @Path("/mfa/enroll/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public MfaEnrollmentConfirmResponse confirmEnrollment(MfaCodeRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("MFA_CODE_REQUIRED", "An authenticator code is required.");
        }
        MfaEnrollmentConfirmResponse response = new MfaEnrollmentConfirmResponse();
        response.recoveryCodes = authService.confirmMfaEnrollment(request.code);
        response.activatedAt = Instant.now();
        return response;
    }

    @POST
    @Path("/mfa/disable")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public Response disableMfa(MfaDisableRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("MFA_CODE_REQUIRED", "Password and code are required.");
        }
        authService.disableMfa(request.password, request.code);
        return Response.noContent().build();
    }

    @POST
    @Path("/mfa/recovery-codes/regenerate")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRoles({"HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "RECEPTIONIST", "DOCTOR", "NURSE",
            "LAB_TECHNICIAN", "LAB_SUPERVISOR", "PHARMACIST", "ACCOUNTANT", "INVENTORY_OFFICER"})
    public MfaRecoveryCodesResponse regenerateRecoveryCodes(MfaDisableRequest request) {
        if (request == null) {
            throw BusinessException.badRequest("MFA_CODE_REQUIRED", "Password and code are required.");
        }
        MfaRecoveryCodesResponse response = new MfaRecoveryCodesResponse();
        response.recoveryCodes = authService.regenerateRecoveryCodes(request.password, request.code);
        response.generatedAt = Instant.now();
        return response;
    }

    private static LoginResponse toResponse(AuthService.AuthSession session) {
        LoginResponse response = new LoginResponse();
        response.token = session.token();
        response.expiresAt = session.expiresAt();
        response.mustChangePassword = session.mustChangePassword();
        response.passwordRotationEnforced = SecuritySettings.enforcePasswordRotation();
        response.mfaRequired = session.mfaRequired();
        response.mfaChallengeToken = session.mfaChallengeToken();
        response.mfaEnrollmentRequired = session.mfaEnrollmentRequired();
        response.user = session.user() == null ? null : CurrentSessionResponse.from(session.user());
        return response;
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) return value.substring(7).trim();
        return null;
    }
}
