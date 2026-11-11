package com.smarthospital.laboratory.service;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.clinical.entity.EncounterStatus;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.laboratory.dto.LabOrderCreateRequest;
import com.smarthospital.laboratory.dto.LabOrderItemResponse;
import com.smarthospital.laboratory.dto.LabOrderResponse;
import com.smarthospital.laboratory.dto.LabResultCreateRequest;
import com.smarthospital.laboratory.dto.LabResultResponse;
import com.smarthospital.laboratory.dto.LabSpecimenRequest;
import com.smarthospital.laboratory.dto.LabTestResponse;
import com.smarthospital.laboratory.entity.LabItemStatus;
import com.smarthospital.laboratory.entity.LabOrder;
import com.smarthospital.laboratory.entity.LabOrderItem;
import com.smarthospital.laboratory.entity.LabOrderPriority;
import com.smarthospital.laboratory.entity.LabOrderStatus;
import com.smarthospital.laboratory.entity.LabResult;
import com.smarthospital.laboratory.entity.LabTestCatalog;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.StaffMember;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class LaboratoryService {
    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService currentUserService;

    @EJB
    private AuditService auditService;
    public LabOrderResponse createOrder(UUID encounterId, LabOrderCreateRequest request) {
        if (request == null || request.testIds == null || request.testIds.isEmpty()) {
            throw BusinessException.badRequest("LAB_ORDER_TESTS_REQUIRED", "Select at least one laboratory test.");
        }
        if (request.testIds.size() > 25 || new HashSet<>(request.testIds).size() != request.testIds.size()) {
            throw BusinessException.badRequest("LAB_ORDER_TEST_LIST_INVALID", "A laboratory order must contain 1–25 unique test items.");
        }

        UUID doctorStaffId = currentUserService.requireDoctorStaffId();
        Encounter encounter = requireEncounterForDoctor(encounterId, doctorStaffId, true);
        DoctorProfile doctor = em.find(DoctorProfile.class, doctorStaffId);
        if (!doctor.canOrderLabs()) {
            throw BusinessException.forbidden("LAB_ORDER_NOT_PERMITTED", "The doctor profile is not permitted to order laboratory tests.");
        }

        List<LabTestCatalog> tests = em.createQuery(
                        "select t from LabTestCatalog t where t.id in :ids and t.active=true",
                        LabTestCatalog.class)
                .setParameter("ids", request.testIds)
                .getResultList();
        if (tests.size() != request.testIds.size()) {
            throw BusinessException.badRequest("LAB_TEST_NOT_AVAILABLE", "One or more selected laboratory tests are missing or inactive.");
        }
        Map<UUID, LabTestCatalog> byId = new HashMap<>();
        tests.forEach(test -> byId.put(test.getId(), test));

        LabOrder order = LabOrder.create(
                encounter,
                doctor,
                request.priority == null ? LabOrderPriority.ROUTINE : request.priority,
                clean(request.clinicalQuestion));
        em.persist(order);
        List<LabOrderItem> items = new ArrayList<>();
        for (UUID testId : request.testIds) {
            LabOrderItem item = LabOrderItem.pending(order, byId.get(testId));
            em.persist(item);
            items.add(item);
        }
        em.flush();

        enqueueEvent("LAB_ORDER_CREATED", "LAB_ORDER", order.getId(), "lab-order-created:" + order.getId());
        auditService.recordAction("LAB_ORDER_CREATED", "LAB_ORDER", order.getId(), "laboratory order created", encounter.getBranchId());
        return toOrderResponse(order, items, false);
    }
    public List<LabTestResponse> searchTests(String query, int limit) {
        String normalized = query == null ? "" : query.trim().replace("%", "").replace("_", "");
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw BusinessException.badRequest("LAB_TEST_QUERY_INVALID", "Enter between 2 and 80 searchable characters.");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return em.createQuery(
                        "select t from LabTestCatalog t where t.active=true "
                                + "and (lower(t.code) like :pattern or lower(t.name) like :pattern) "
                                + "order by t.name",
                        LabTestCatalog.class)
                .setParameter("pattern", "%" + normalized.toLowerCase(java.util.Locale.ROOT) + "%")
                .setMaxResults(safeLimit)
                .getResultList().stream().map(LabTestResponse::from).toList();
    }
    public List<LabOrderResponse> listBranchOrders(UUID branchId, String status, int limit) {
        CurrentUser user = currentUserService.requireCurrentUser();
        currentUserService.requireBranchAccess(branchId);
        StaffMember operator = requireLabOperator(branchId, user);
        LabOrderStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = LabOrderStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("LAB_ORDER_STATUS_INVALID", "The requested laboratory order status is not supported.");
            }
        }

        var query = em.createQuery(
                "select distinct o from LabOrder o join fetch o.encounter e join fetch e.patient p "
                        + "where e.branchId=:branchId "
                        + "and exists (select pba.id from PatientBranchAccess pba "
                        + "where pba.patient=e.patient and pba.branch.id=e.branchId and pba.revokedAt is null) "
                        + "and (:status is null or o.status=:status) "
                        + "order by o.orderedAt asc",
                LabOrder.class);
        query.setParameter("branchId", branchId);
        query.setParameter("status", statusFilter);
        query.setMaxResults(Math.min(Math.max(limit, 1), 100));

        List<LabOrder> orders = query.getResultList();
        Map<UUID, List<LabOrderItem>> itemsByOrder = findItemsForOrders(orders);
        List<LabOrderResponse> responses = new ArrayList<>();
        for (LabOrder order : orders) {
            responses.add(toOrderResponse(order, itemsByOrder.getOrDefault(order.getId(), List.of()), true));
        }
        return responses;
    }
    public LabOrderItemResponse collectSpecimen(UUID itemId, LabSpecimenRequest request) {
        if (request == null || request.specimenCode == null || request.specimenCode.isBlank()) {
            throw BusinessException.badRequest("SPECIMEN_CODE_REQUIRED", "A specimen identifier is required.");
        }
        LabOrderItem item = requireItemForLabUpdate(itemId);
        UUID branchId = item.getLabOrder().getEncounter().getBranchId();
        CurrentUser user = currentUserService.requireCurrentUser();
        StaffMember operator = requireLabOperator(branchId, user);
        try {
            item.collect(request.specimenCode.trim(), operator);
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("LAB_ITEM_NOT_COLLECTABLE", exception.getMessage());
        }
        updateOrderProgress(item.getLabOrder());
        em.flush();
        auditService.recordAction("LAB_SPECIMEN_COLLECTED", "LAB_ORDER_ITEM", item.getId(), "specimen collected", branchId);
        return LabOrderItemResponse.from(item, item.getLabResult());
    }
    public LabOrderItemResponse startProcessing(UUID itemId) {
        LabOrderItem item = requireItemForLabUpdate(itemId);
        UUID branchId = item.getLabOrder().getEncounter().getBranchId();
        StaffMember operator = requireLabOperator(branchId, currentUserService.requireCurrentUser());
        try {
            item.startProcessing();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("LAB_ITEM_NOT_PROCESSABLE", exception.getMessage());
        }
        updateOrderProgress(item.getLabOrder());
        em.flush();
        auditService.recordAction("LAB_TEST_PROCESSING_STARTED", "LAB_ORDER_ITEM", item.getId(), "laboratory processing started", branchId);
        return LabOrderItemResponse.from(item, item.getLabResult());
    }
    public LabResultResponse recordResult(UUID itemId, LabResultCreateRequest request) {
        if (request == null || ((request.resultText == null || request.resultText.isBlank()) && request.resultNumeric == null)) {
            throw BusinessException.badRequest("LAB_RESULT_VALUE_REQUIRED", "Enter a textual or numeric result.");
        }
        if (request.resultNumeric != null) {
            BigDecimal normalized = request.resultNumeric.stripTrailingZeros();
            int scale = Math.max(normalized.scale(), 0);
            int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
            if (scale > 5 || integerDigits > 11 || normalized.precision() > 16) {
                throw BusinessException.badRequest("LAB_RESULT_NUMERIC_INVALID",
                        "The numeric result must fit NUMERIC(16,5): up to 11 integer digits and 5 fractional digits.");
            }
        }

        LabOrderItem item = requireItemForLabUpdate(itemId);
        UUID branchId = item.getLabOrder().getEncounter().getBranchId();
        StaffMember operator = requireLabOperator(branchId, currentUserService.requireCurrentUser());
        LabResult existing = findResult(itemId);
        if (existing != null) {
            throw BusinessException.conflict("LAB_RESULT_ALREADY_EXISTS", "A result has already been recorded for this test item.");
        }

        LabTestCatalog test = item.getLabTest();
        LabResult result = LabResult.record(
                item,
                clean(request.resultText),
                request.resultNumeric,
                clean(request.resultUnit) == null ? test.getResultUnit() : request.resultUnit.trim(),
                clean(request.referenceRange) == null ? test.getReferenceRange() : request.referenceRange.trim(),
                request.abnormalFlag,
                operator);
        try {
            item.markResulted();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("LAB_ITEM_NOT_READY_FOR_RESULT", exception.getMessage());
        }
        em.persist(result);
        updateOrderProgress(item.getLabOrder());
        em.flush();
        auditService.recordAction("LAB_RESULT_RECORDED", "LAB_RESULT", result.getId(), "laboratory result recorded", branchId);
        return LabResultResponse.from(result);
    }
    public LabResultResponse reviewAndReleaseResult(UUID itemId) {
        LabOrderItem item = requireItemForLabUpdate(itemId);
        UUID branchId = item.getLabOrder().getEncounter().getBranchId();
        StaffMember reviewer = requireLabOperator(branchId, currentUserService.requireCurrentUser());
        LabResult result = findResult(itemId);
        if (result == null) {
            throw BusinessException.notFound("Laboratory result");
        }
        try {
            result.reviewAndRelease(reviewer);
            item.markReviewed();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("LAB_RESULT_REVIEW_CONFLICT", exception.getMessage());
        }
        updateOrderProgress(item.getLabOrder());
        em.flush();
        enqueueEvent("LAB_RESULT_RELEASED", "LAB_RESULT", result.getId(), "lab-result-released:" + result.getId());
        auditService.recordAction("LAB_RESULT_RELEASED", "LAB_RESULT", result.getId(), "laboratory result reviewed and released", branchId);
        return LabResultResponse.from(result);
    }
    public List<LabOrderResponse> listEncounterOrders(UUID encounterId) {
        UUID doctorStaffId = currentUserService.requireDoctorStaffId();
        Encounter encounter = requireEncounterForDoctor(encounterId, doctorStaffId, false);
        List<LabOrder> orders = em.createQuery(
                        "select o from LabOrder o where o.encounter.id=:encounterId order by o.orderedAt desc",
                        LabOrder.class)
                .setParameter("encounterId", encounter.getId())
                .setMaxResults(100)
                .getResultList();
        Map<UUID, List<LabOrderItem>> itemsByOrder = findItemsForOrders(orders);
        List<LabOrderResponse> responses = new ArrayList<>();
        for (LabOrder order : orders) {
            responses.add(toOrderResponse(order, itemsByOrder.getOrDefault(order.getId(), List.of()), false));
        }
        return responses;
    }

    private Encounter requireEncounterForDoctor(UUID encounterId, UUID doctorStaffId, boolean lock) {
        Encounter encounter = lock
                ? em.find(Encounter.class, encounterId, LockModeType.PESSIMISTIC_WRITE)
                : em.find(Encounter.class, encounterId);
        if (encounter == null) {
            throw BusinessException.notFound("Encounter");
        }
        currentUserService.requireBranchAccess(encounter.getBranchId());
        currentUserService.requirePatientAccessInBranch(encounter.getPatientId(), encounter.getBranchId());
        if (!encounter.getAttendingDoctorId().equals(doctorStaffId)) {
            throw BusinessException.forbidden("ATTENDING_DOCTOR_REQUIRED", "Only the attending doctor may manage laboratory orders for this encounter.");
        }
        if (lock && encounter.getStatus() != EncounterStatus.OPEN && encounter.getStatus() != EncounterStatus.IN_PROGRESS) {
            throw BusinessException.conflict("ENCOUNTER_NOT_ACTIVE", "Laboratory orders cannot be changed after the encounter is completed.");
        }
        return encounter;
    }

    private LabOrderItem requireItemForLabUpdate(UUID itemId) {
        LabOrderItem item = em.find(LabOrderItem.class, itemId, LockModeType.PESSIMISTIC_WRITE);
        if (item == null) {
            throw BusinessException.notFound("Laboratory order item");
        }
        requireLabOperator(item.getLabOrder().getEncounter().getBranchId(), currentUserService.requireCurrentUser());
        if (item.getLabOrder().getStatus() == LabOrderStatus.CANCELLED) {
            throw BusinessException.conflict("LAB_ORDER_CANCELLED", "The laboratory order has been cancelled.");
        }
        return item;
    }

    private StaffMember requireLabOperator(UUID branchId, CurrentUser user) {
        currentUserService.requireBranchAccess(branchId);
        boolean technician = user.roleCodes().contains("LAB_TECHNICIAN");
        boolean supervisor = user.roleCodes().contains("LAB_SUPERVISOR");
        if (!technician && !supervisor) {
            throw BusinessException.forbidden("LAB_ROLE_REQUIRED", "A laboratory technician or supervisor role is required.");
        }
        UUID staffId = currentUserService.requireStaffId();
        StaffMember staff = em.find(StaffMember.class, staffId);
        if (staff == null || !staff.isActive() || !staff.getBranch().getId().equals(branchId)
                || staff.getDepartment() == null
                || !staff.getDepartment().isActive()
                || !staff.getDepartment().getBranch().getId().equals(branchId)
                || !"LABORATORY".equals(staff.getDepartment().getDepartmentType())) {
            throw BusinessException.forbidden("LAB_STAFF_SCOPE_REQUIRED", "The user must be an active staff member of the laboratory department in this branch.");
        }
        return staff;
    }

    private LabResult findResult(UUID itemId) {
        List<LabResult> results = em.createQuery(
                        "select r from LabResult r where r.labOrderItem.id=:itemId",
                        LabResult.class)
                .setParameter("itemId", itemId)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private Map<UUID, List<LabOrderItem>> findItemsForOrders(List<LabOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        List<UUID> orderIds = orders.stream().map(LabOrder::getId).toList();
        List<LabOrderItem> items = em.createQuery(
                        "select i from LabOrderItem i join fetch i.labTest left join fetch i.labResult "
                                + "where i.labOrder.id in :orderIds order by i.labOrder.id, i.id",
                        LabOrderItem.class)
                .setParameter("orderIds", orderIds)
                .getResultList();
        Map<UUID, List<LabOrderItem>> grouped = new HashMap<>();
        for (LabOrderItem item : items) {
            grouped.computeIfAbsent(item.getLabOrderId(), ignored -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private List<LabOrderItem> findItems(UUID orderId) {
        return em.createQuery(
                        "select i from LabOrderItem i join fetch i.labTest left join fetch i.labResult "
                                + "where i.labOrder.id=:orderId order by i.id",
                        LabOrderItem.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    private LabOrderResponse toOrderResponse(LabOrder order, List<LabOrderItem> items, boolean includeUnreleasedResults) {
        Map<UUID, LabResult> results = new HashMap<>();
        for (LabOrderItem item : items) {
            if (item.getLabResult() != null) results.put(item.getId(), item.getLabResult());
        }
        return LabOrderResponse.from(order, items, results, includeUnreleasedResults);
    }

    private void updateOrderProgress(LabOrder order) {
        List<LabOrderItem> items = findItems(order.getId());
        boolean allResolved = items.stream().allMatch(item ->
                item.getStatus() == LabItemStatus.REVIEWED || item.getStatus() == LabItemStatus.CANCELLED);
        boolean anyResult = items.stream().anyMatch(item ->
                item.getStatus() == LabItemStatus.RESULTED || item.getStatus() == LabItemStatus.REVIEWED);
        boolean anyStarted = items.stream().anyMatch(item ->
                item.getStatus() == LabItemStatus.COLLECTED || item.getStatus() == LabItemStatus.PROCESSING);
        if (allResolved) {
            order.updateStatus(LabOrderStatus.COMPLETED);
        } else if (anyResult) {
            order.updateStatus(LabOrderStatus.PARTIALLY_COMPLETED);
        } else if (anyStarted) {
            order.updateStatus(LabOrderStatus.IN_PROGRESS);
        } else {
            order.updateStatus(LabOrderStatus.ACCEPTED);
        }
    }

    private void enqueueEvent(String eventType, String aggregateType, UUID aggregateId, String idempotencyKey) {
        String payload = "{\"eventType\":\"" + eventType + "\",\"aggregateType\":\""
                + aggregateType + "\",\"aggregateId\":\"" + aggregateId + "\"}";
        em.createNativeQuery("""
                INSERT INTO hospital.message_outbox
                    (event_type, aggregate_type, aggregate_id, idempotency_key, payload)
                VALUES (?1, ?2, ?3, ?4, CAST(?5 AS JSON))
                ON DUPLICATE KEY UPDATE idempotency_key=VALUES(idempotency_key)
                """)
                .setParameter(1, eventType)
                .setParameter(2, aggregateType)
                .setParameter(3, aggregateId)
                .setParameter(4, idempotencyKey)
                .setParameter(5, payload)
                .executeUpdate();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
