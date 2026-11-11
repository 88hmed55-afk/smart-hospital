package com.smarthospital.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import jakarta.validation.constraints.Size;
import java.util.List;

public class DispenseCreateRequest {
    @NotNull
    public UUID locationId;

    @NotEmpty
    @Size(max = 30)
    @Valid
    public List<DispenseItemRequest> items;
}
