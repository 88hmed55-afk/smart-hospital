package com.smarthospital.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class TrialBalanceResponse {
    public UUID branchId;
    public LocalDate asOfDate;
    public String currencyCode;
    public List<AccountBalance> accounts;
    public BigDecimal totalDebits;
    public BigDecimal totalCredits;
    public BigDecimal totalDebitBalances;
    public BigDecimal totalCreditBalances;
    public boolean balanced;
    public static class AccountBalance {
        public UUID accountId;
        public String accountCode;
        public String accountName;
        public String accountType;
        public String normalBalance;
        public BigDecimal debitMovement;
        public BigDecimal creditMovement;
        public BigDecimal closingDebit;
        public BigDecimal closingCredit;
    }
}
