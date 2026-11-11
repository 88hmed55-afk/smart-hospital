package com.smarthospital.laboratory.dto;

import com.smarthospital.laboratory.entity.LabOrderPriority;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class LabOrderCreateRequest {
    public LabOrderPriority priority = LabOrderPriority.ROUTINE;

    @Size(max = 500)
    public String clinicalQuestion;

    @NotEmpty
    @Size(max = 25)
    public List<UUID> testIds;
}
