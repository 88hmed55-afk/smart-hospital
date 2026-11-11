package com.smarthospital.accounting.dto;

import java.util.UUID;

public class AccountResponse {
    public UUID id;
    public UUID branchId;
    public String accountCode;
    public String accountName;
    public String accountType;
    public String normalBalance;
    public UUID parentAccountId;
    public boolean controlAccount;
    public boolean allowsPosting;
    public boolean active;
}
