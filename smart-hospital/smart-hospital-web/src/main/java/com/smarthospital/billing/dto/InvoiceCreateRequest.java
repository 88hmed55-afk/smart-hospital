package com.smarthospital.billing.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public class InvoiceCreateRequest { @NotNull public UUID branchId; @NotNull public UUID encounterId; @NotEmpty @Size(max=40) @Valid public List<InvoiceItemCreateRequest> items; }
