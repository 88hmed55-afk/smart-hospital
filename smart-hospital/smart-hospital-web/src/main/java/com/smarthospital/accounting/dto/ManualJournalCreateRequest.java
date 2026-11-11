package com.smarthospital.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ManualJournalCreateRequest {
    @NotNull public UUID branchId;
    @NotNull public LocalDate entryDate;
    @NotBlank @Size(max = 1000) public String description;
    @NotNull @Size(min = 2, max = 50) public List<@Valid Line> lines;
    public static class Line {
        @NotBlank @Size(max = 32) public String accountCode;
        @NotNull public java.math.BigDecimal debitAmount;
        @NotNull public java.math.BigDecimal creditAmount;
        @Size(max = 500) public String memo;
    }
}
