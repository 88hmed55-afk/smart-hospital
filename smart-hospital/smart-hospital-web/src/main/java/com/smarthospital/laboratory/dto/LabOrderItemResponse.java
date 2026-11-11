package com.smarthospital.laboratory.dto;

import com.smarthospital.laboratory.entity.LabOrderItem;
import com.smarthospital.laboratory.entity.LabResult;
import java.time.Instant;
import java.util.UUID;

public class LabOrderItemResponse {
    public UUID id;
    public UUID testId;
    public String testCode;
    public String testName;
    public String status;
    public String specimenCode;
    public Instant collectedAt;
    public UUID collectedBy;
    public LabResultResponse result;

    public static LabOrderItemResponse from(LabOrderItem item, LabResult result) {
        return from(item, result, true);
    }

    public static LabOrderItemResponse from(LabOrderItem item, LabResult result, boolean includeUnreleasedResult) {
        LabOrderItemResponse response = new LabOrderItemResponse();
        response.id = item.getId();
        response.testId = item.getLabTestId();
        response.testCode = item.getTestCode();
        response.testName = item.getTestName();
        response.status = item.getStatus().name();
        response.specimenCode = item.getSpecimenCode();
        response.collectedAt = item.getCollectedAt();
        response.collectedBy = item.getCollectedBy();
        boolean visible = result != null && (includeUnreleasedResult || result.getReleasedAt() != null);
        response.result = visible ? LabResultResponse.from(result) : null;
        return response;
    }
}
