package com.smarthospital.accounting.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "hospital", name = "chart_of_accounts",
       uniqueConstraints = @UniqueConstraint(name = "uq_account_branch_code", columnNames = {"branch_id", "account_code"}))
public class ChartOfAccount {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "branch_id", nullable = false, updatable = false) private UUID branchId;
    @Column(name = "account_code", nullable = false, length = 32) private String accountCode;
    @Column(name = "account_name", nullable = false, length = 160) private String accountName;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false, length = 16) private AccountType accountType;
    @Enumerated(EnumType.STRING) @Column(name = "normal_balance", nullable = false, length = 8) private NormalBalance normalBalance;
    @Column(name = "parent_account_id") private UUID parentAccountId;
    @Column(name = "is_control_account", nullable = false) private boolean controlAccount;
    @Column(name = "allows_posting", nullable = false) private boolean allowsPosting;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ChartOfAccount() { }
    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public String getAccountCode() { return accountCode; }
    public String getAccountName() { return accountName; }
    public AccountType getAccountType() { return accountType; }
    public NormalBalance getNormalBalance() { return normalBalance; }
    public UUID getParentAccountId() { return parentAccountId; }
    public boolean isControlAccount() { return controlAccount; }
    public boolean isAllowsPosting() { return allowsPosting; }
    public boolean isActive() { return active; }
}
