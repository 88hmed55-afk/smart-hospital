package com.smarthospital.security.auth.dto;

/** Self-service credential rotation. */
public class ChangePasswordRequest {
    public String currentPassword;
    public String newPassword;
}
