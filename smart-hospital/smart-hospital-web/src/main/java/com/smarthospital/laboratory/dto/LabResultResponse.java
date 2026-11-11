package com.smarthospital.laboratory.dto;

import com.smarthospital.laboratory.entity.LabResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class LabResultResponse {
    public UUID id;
    public UUID labOrderItemId;
    public String resultText;
    public BigDecimal resultNumeric;
    public String resultUnit;
    public String referenceRange;
    public String abnormalFlag;
    public UUID performedBy;
    public UUID reviewedBy;
    public Instant performedAt;
    public Instant reviewedAt;
    public Instant releasedAt;

    public static LabResultResponse from(LabResult result) {
        LabResultResponse response = new LabResultResponse();
        response.id = result.getId();
        response.labOrderItemId = result.getLabOrderItemId();
        response.resultText = result.getResultText();
        response.resultNumeric = result.getResultNumeric();
        response.resultUnit = result.getResultUnit();
        response.referenceRange = result.getReferenceRange();
        response.abnormalFlag = result.getAbnormalFlag().name();
        response.performedBy = result.getPerformedBy();
        response.reviewedBy = result.getReviewedBy();
        response.performedAt = result.getPerformedAt();
        response.reviewedAt = result.getReviewedAt();
        response.releasedAt = result.getReleasedAt();
        return response;
    }
}
