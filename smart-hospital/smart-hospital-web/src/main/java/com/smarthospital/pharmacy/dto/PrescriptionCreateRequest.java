package com.smarthospital.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class PrescriptionCreateRequest {
    @NotEmpty
    @Size(max = 30)
    @Valid
    public List<PrescriptionItemRequest> items;

    /**
     * Mandatory clinical justification when a blocking allergy or contraindicated/major
     * drug interaction is overridden by the prescriber. Recorded in the audit trail.
     */
    @Size(max = 500)
    public String overrideReason;
}
