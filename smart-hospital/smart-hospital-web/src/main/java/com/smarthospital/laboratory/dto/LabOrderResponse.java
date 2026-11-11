package com.smarthospital.laboratory.dto;

import com.smarthospital.laboratory.entity.LabOrder;
import com.smarthospital.laboratory.entity.LabOrderItem;
import com.smarthospital.laboratory.entity.LabResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class LabOrderResponse {
    public UUID id;
    public UUID encounterId;
    public UUID orderedBy;
    public String patientMrn;
    public String patientDisplayName;
    public String priority;
    public String status;
    public String clinicalQuestion;
    public Instant orderedAt;
    public List<LabOrderItemResponse> items;

    public static LabOrderResponse from(LabOrder order, List<LabOrderItem> items, java.util.Map<UUID, LabResult> results, boolean includeUnreleasedResults) {
        LabOrderResponse response = new LabOrderResponse();
        response.id = order.getId();
        response.encounterId = order.getEncounterId();
        response.orderedBy = order.getOrderedBy();
        var patient = order.getEncounter().getPatient();
        response.patientMrn = patient.getMrn();
        response.patientDisplayName = String.join(" ",
                java.util.stream.Stream.of(patient.getFirstName(), patient.getMiddleName(), patient.getLastName())
                        .filter(value -> value != null && !value.isBlank())
                        .toList());
        response.priority = order.getPriority().name();
        response.status = order.getStatus().name();
        response.clinicalQuestion = order.getClinicalQuestion();
        response.orderedAt = order.getOrderedAt();
        response.items = items.stream()
                .map(item -> LabOrderItemResponse.from(item, results.get(item.getId()), includeUnreleasedResults))
                .toList();
        return response;
    }
}
