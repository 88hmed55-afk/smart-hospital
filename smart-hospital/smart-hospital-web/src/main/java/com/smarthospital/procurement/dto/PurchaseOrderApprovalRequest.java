package com.smarthospital.procurement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PurchaseOrderApprovalRequest {
    @NotNull public Boolean approved;
    @Size(max = 500) public String note;
}
