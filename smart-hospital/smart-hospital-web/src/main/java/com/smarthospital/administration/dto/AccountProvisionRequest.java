package com.smarthospital.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Creates an application-managed account for an existing staff member.
 *
 * <p>The administrator supplies an initial password that satisfies the password
 * policy; the account is flagged {@code must_change_password} so the employee must
 * choose a private password at first sign-in.</p>
 */
public class AccountProvisionRequest {
    @NotNull public UUID staffId;
    @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]{3,80}") public String username;
    @NotBlank @Size(min = 12, max = 128) public String initialPassword;
    @NotEmpty @Size(max = 10) public List<@NotBlank @Size(max = 48) String> roleCodes;
    @NotEmpty @Size(max = 10) public List<@NotNull UUID> branchIds;
    @NotNull public UUID defaultBranchId;
}
