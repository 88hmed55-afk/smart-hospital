package com.smarthospital.procurement.service;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.entity.HospitalBranch;
import com.smarthospital.pharmacy.entity.InventoryLocation;
import com.smarthospital.pharmacy.entity.Medication;
import com.smarthospital.pharmacy.entity.MedicationBatch;
import com.smarthospital.pharmacy.entity.StockBalance;
import com.smarthospital.pharmacy.entity.StockMovement;
import com.smarthospital.pharmacy.dto.PharmacyLocationResponse;
import com.smarthospital.procurement.dto.*;
import com.smarthospital.procurement.entity.*;
import com.smarthospital.security.domain.CurrentUser;
import com.smarthospital.security.service.CurrentUserService;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class ProcurementService {
    private static final Set<String> ORDER_STATUSES = Set.of(
            "DRAFT", "SUBMITTED", "APPROVED", "ORDERED", "PARTIALLY_RECEIVED", "RECEIVED", "REJECTED", "CANCELLED");

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;
    @EJB private CurrentUserService users;
    @EJB private AuditService audit;
    public List<SupplierResponse> listSuppliers(String query, int limit) {
        users.requireCurrentUser();
        String normalized = normalize(query);
        if (!normalized.isEmpty() && normalized.length() < 2) {
            throw BusinessException.badRequest("SUPPLIER_QUERY_TOO_SHORT", "Enter at least two characters or leave the search empty.");
        }
        if (normalized.length() > 80) throw BusinessException.badRequest("SUPPLIER_QUERY_TOO_LONG", "Supplier search is limited to 80 characters.");
        String pattern = normalized.isEmpty() ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        return em.createQuery("select s from Supplier s where s.status <> 'BLOCKED' and "
                        + "(:pattern is null or lower(s.supplierCode) like :pattern or lower(s.supplierName) like :pattern) "
                        + "order by s.supplierName", Supplier.class)
                .setParameter("pattern", pattern)
                .setMaxResults(safeLimit(limit, 100))
                .getResultList().stream().map(SupplierResponse::from).toList();
    }
    public SupplierResponse createSupplier(SupplierCreateRequest request) {
        if (request == null) throw BusinessException.badRequest("SUPPLIER_REQUEST_REQUIRED", "Supplier details are required.");
        String code = requiredText(request.supplierCode, 32, "SUPPLIER_CODE_INVALID");
        String name = requiredText(request.supplierName, 180, "SUPPLIER_NAME_INVALID");
        users.requireCurrentUser();
        Long exists = em.createQuery("select count(s) from Supplier s where lower(s.supplierCode)=:code", Long.class)
                .setParameter("code", code.toLowerCase(Locale.ROOT)).getSingleResult();
        if (exists > 0) throw BusinessException.conflict("SUPPLIER_CODE_EXISTS", "A supplier with this code already exists.");
        Supplier supplier = Supplier.create(code, name, clean(request.contactPerson), clean(request.phoneNumber),
                clean(request.email), clean(request.address), clean(request.registrationNumber));
        em.persist(supplier);
        em.flush();
        audit.recordAction("SUPPLIER_CREATED", "SUPPLIER", supplier.getId(), "supplier master created");
        return SupplierResponse.from(supplier);
    }
    public List<PharmacyLocationResponse> listInventoryLocations(UUID branchId) {
        requireInventoryAccess(branchId);
        return em.createQuery("select l from InventoryLocation l where l.branch.id=:branchId and l.active=true "
                        + "and l.locationType in ('MAIN_STORE','PHARMACY') order by l.locationType, l.code", InventoryLocation.class)
                .setParameter("branchId", branchId)
                .getResultList().stream().map(PharmacyLocationResponse::from).toList();
    }
    public List<PurchaseOrderResponse> listOrders(UUID branchId, String status, int limit) {
        requireInventoryAccess(branchId);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !ORDER_STATUSES.contains(normalizedStatus)) {
            throw BusinessException.badRequest("PURCHASE_ORDER_STATUS_INVALID", "The requested purchase order status is not supported.");
        }
        // Paginate parent IDs first. Applying setMaxResults to a collection fetch join can
        // truncate parent rows or force provider-specific in-memory pagination.
        List<UUID> orderIds = em.createQuery(
                        "select o.id from PurchaseOrder o "
                                + "where o.branch.id=:branchId and (:status is null or o.status=:status) "
                                + "order by o.createdAt desc, o.id desc", UUID.class)
                .setParameter("branchId", branchId)
                .setParameter("status", normalizedStatus)
                .setMaxResults(safeLimit(limit, 100))
                .getResultList();
        if (orderIds.isEmpty()) return List.of();

        List<PurchaseOrder> fetched = em.createQuery(
                        "select distinct o from PurchaseOrder o join fetch o.supplier "
                                + "left join fetch o.items i left join fetch i.medication "
                                + "where o.id in :ids", PurchaseOrder.class)
                .setParameter("ids", orderIds)
                .getResultList();
        Map<UUID, PurchaseOrder> byId = new HashMap<>();
        fetched.forEach(order -> byId.put(order.getId(), order));
        return orderIds.stream().map(byId::get).filter(Objects::nonNull)
                .map(PurchaseOrderResponse::from).toList();
    }
    public PurchaseOrderResponse createDraft(PurchaseOrderCreateRequest request) {
        if (request == null || request.items == null || request.items.isEmpty() || request.items.size() > 40) {
            throw BusinessException.badRequest("PURCHASE_ORDER_ITEMS_REQUIRED", "A purchase order requires between 1 and 40 items.");
        }
        CurrentUser actor = requireInventoryAccess(request.branchId);
        HospitalBranch branch = em.find(HospitalBranch.class, request.branchId);
        if (branch == null || !branch.isActive()) throw BusinessException.notFound("Active hospital branch");
        Supplier supplier = em.find(Supplier.class, request.supplierId);
        if (supplier == null || !"ACTIVE".equals(supplier.getStatus())) throw BusinessException.notFound("Active supplier");
        if (request.expectedDeliveryOn != null && request.expectedDeliveryOn.isBefore(today(branch))) {
            throw BusinessException.badRequest("EXPECTED_DELIVERY_DATE_INVALID", "Expected delivery date cannot be in the past.");
        }

        Set<UUID> medicationIds = new HashSet<>();
        PurchaseOrder order = PurchaseOrder.draft(branch, supplier, generatedNumber("PO", today(branch)), actor.userId(),
                request.expectedDeliveryOn, clean(request.notes));
        int line = 1;
        for (PurchaseOrderItemCreateRequest itemRequest : request.items) {
            if (itemRequest == null || itemRequest.medicationId == null || !medicationIds.add(itemRequest.medicationId)) {
                throw BusinessException.badRequest("PURCHASE_ORDER_MEDICATION_INVALID", "Medication identifiers must be present and unique in a purchase order.");
            }
            BigDecimal quantity = decimal(itemRequest.quantity, 3, true, "PURCHASE_ORDER_QUANTITY_INVALID");
            BigDecimal unitCost = decimal(itemRequest.unitCost, 2, false, "PURCHASE_ORDER_COST_INVALID");
            BigDecimal discount = decimal(itemRequest.lineDiscount == null ? BigDecimal.ZERO : itemRequest.lineDiscount,
                    2, false, "PURCHASE_ORDER_DISCOUNT_INVALID");
            BigDecimal gross = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            if (discount.compareTo(gross) > 0) throw BusinessException.badRequest("PURCHASE_ORDER_DISCOUNT_EXCEEDS_LINE", "Line discount cannot exceed the gross line amount.");
            Medication medication = em.find(Medication.class, itemRequest.medicationId);
            if (medication == null || !medication.isActive()) throw BusinessException.notFound("Active medication");
            String unit = clean(itemRequest.unitOfMeasure);
            if (unit == null) unit = medication.getDispensingUnit();
            if (unit.isBlank() || unit.length() > 32) throw BusinessException.badRequest("PURCHASE_ORDER_UNIT_INVALID", "Unit of measure is invalid.");
            order.addItem(PurchaseOrderItem.create(order, medication, line++, quantity, unit, unitCost, discount));
        }
        order.recalculate();
        em.persist(order);
        em.flush();
        audit.recordAction("PURCHASE_ORDER_DRAFT_CREATED", "PURCHASE_ORDER", order.getId(), "purchase order draft created", request.branchId);
        return PurchaseOrderResponse.from(order);
    }
    public PurchaseOrderResponse submit(UUID orderId) {
        PurchaseOrder order = lockOrder(orderId);
        requireInventoryAccess(order.getBranchId());
        if (order.getItems().isEmpty()) throw BusinessException.conflict("PURCHASE_ORDER_EMPTY", "An empty purchase order cannot be submitted.");
        try { order.submit(); } catch (IllegalStateException ex) { throw BusinessException.conflict("PURCHASE_ORDER_NOT_DRAFT", "Only draft purchase orders can be submitted."); }
        em.flush();
        audit.recordAction("PURCHASE_ORDER_SUBMITTED", "PURCHASE_ORDER", order.getId(), "purchase order submitted for approval", order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }
    public PurchaseOrderResponse approve(UUID orderId, boolean approved, String note) {
        PurchaseOrder order = lockOrder(orderId);
        CurrentUser actor = requireApproverAccess(order.getBranchId());
        try {
            if (approved) order.approve(actor.userId()); else order.reject(actor.userId());
        } catch (IllegalStateException ex) {
            throw BusinessException.conflict("PURCHASE_ORDER_NOT_SUBMITTED", "Only submitted purchase orders can be approved or rejected.");
        }
        em.createNativeQuery("INSERT INTO hospital.purchase_order_approvals "
                        + "(purchase_order_id, approval_step, approver_user_id, decision, decision_note, decided_at) "
                        + "VALUES (?1, 1, ?2, ?3, ?4, ?5)")
                .setParameter(1, order.getId()).setParameter(2, actor.userId())
                .setParameter(3, approved ? "APPROVED" : "REJECTED")
                .setParameter(4, clean(note)).setParameter(5, Instant.now()).executeUpdate();
        em.flush();
        audit.recordAction(approved ? "PURCHASE_ORDER_APPROVED" : "PURCHASE_ORDER_REJECTED", "PURCHASE_ORDER",
                order.getId(), approved ? "purchase order approved" : "purchase order rejected", order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }
    public PurchaseOrderResponse markOrdered(UUID orderId) {
        PurchaseOrder order = lockOrder(orderId);
        requireInventoryAccess(order.getBranchId());
        try { order.markOrdered(); } catch (IllegalStateException ex) { throw BusinessException.conflict("PURCHASE_ORDER_NOT_APPROVED", "Only approved purchase orders can be marked as ordered."); }
        em.flush();
        audit.recordAction("PURCHASE_ORDER_MARKED_ORDERED", "PURCHASE_ORDER", order.getId(), "purchase order sent to supplier", order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }
    public GoodsReceiptResponse receive(UUID orderId, GoodsReceiptCreateRequest request) {
        if (request == null || request.items == null || request.items.isEmpty() || request.items.size() > 80) {
            throw BusinessException.badRequest("GOODS_RECEIPT_ITEMS_REQUIRED", "A goods receipt requires between 1 and 80 lines.");
        }
        PurchaseOrder order = lockOrder(orderId);
        CurrentUser actor = requireInventoryAccess(request.branchId);
        if (!order.getBranchId().equals(request.branchId)) throw BusinessException.badRequest("GOODS_RECEIPT_BRANCH_MISMATCH", "Receipt and purchase order must belong to the same branch.");
        if (!"ORDERED".equals(order.getStatus()) && !"PARTIALLY_RECEIVED".equals(order.getStatus())) {
            throw BusinessException.conflict("PURCHASE_ORDER_NOT_RECEIVABLE", "Only ordered or partially received purchase orders can receive goods.");
        }
        InventoryLocation location = em.find(InventoryLocation.class, request.inventoryLocationId);
        if (location == null || !location.isActive() || !order.getBranchId().equals(location.getBranchId())
                || !Set.of("MAIN_STORE", "PHARMACY").contains(location.getLocationType())) {
            throw BusinessException.badRequest("RECEIPT_LOCATION_INVALID", "Choose an active main store or pharmacy location in the purchase order branch.");
        }

        Set<UUID> seenLineIds = new HashSet<>();
        List<PreparedReceiptLine> prepared = new ArrayList<>();
        for (GoodsReceiptLineRequest line : request.items) {
            if (line == null || line.purchaseOrderItemId == null || !seenLineIds.add(line.purchaseOrderItemId)) {
                throw BusinessException.badRequest("GOODS_RECEIPT_LINE_INVALID", "Receipt lines must identify unique purchase order items.");
            }
            PurchaseOrderItem poItem = em.find(PurchaseOrderItem.class, line.purchaseOrderItemId, LockModeType.PESSIMISTIC_WRITE);
            if (poItem == null || !order.getId().equals(poItem.getPurchaseOrder().getId())) {
                throw BusinessException.badRequest("GOODS_RECEIPT_LINE_ORDER_MISMATCH", "A receipt line does not belong to this purchase order.");
            }
            BigDecimal accepted = decimal(line.acceptedQuantity, 3, false, "GOODS_RECEIPT_ACCEPTED_INVALID");
            BigDecimal rejected = decimal(line.rejectedQuantity, 3, false, "GOODS_RECEIPT_REJECTED_INVALID");
            if (accepted.signum() == 0 && rejected.signum() == 0) throw BusinessException.badRequest("GOODS_RECEIPT_ZERO_LINE", "Each receipt line must accept or reject a positive quantity.");
            if (accepted.add(rejected).compareTo(poItem.getRemainingQuantity()) > 0) {
                throw BusinessException.conflict("GOODS_RECEIPT_EXCEEDS_ORDER", "Accepted plus rejected quantity cannot exceed the remaining ordered quantity.");
            }
            if (rejected.signum() > 0 && (line.rejectionReason == null || line.rejectionReason.isBlank())) {
                throw BusinessException.badRequest("GOODS_RECEIPT_REJECTION_REASON_REQUIRED", "A rejection reason is required for rejected quantities.");
            }
            if (accepted.signum() > 0) validateBatchDetails(line, order.getBranch());
            prepared.add(new PreparedReceiptLine(poItem, accepted, rejected, line));
        }

        GoodsReceipt receipt = GoodsReceipt.draft(order.getBranch(), order, location, generatedNumber("GRN", today(order.getBranch())),
                clean(request.supplierDeliveryReference), actor.userId(), clean(request.notes));
        em.persist(receipt);
        em.flush();

        List<StockMovement> movements = new ArrayList<>();
        int receiptLineCount = 0;
        for (PreparedReceiptLine preparedLine : prepared) {
            PurchaseOrderItem poItem = preparedLine.poItem;
            MedicationBatch batch = null;
            if (preparedLine.accepted.signum() > 0) {
                batch = findOrCreateBatch(poItem.getMedication(), preparedLine.request);
                StockBalance balance = em.createQuery("select sb from StockBalance sb where sb.location.id=:locationId and sb.batch.id=:batchId", StockBalance.class)
                        .setParameter("locationId", location.getId()).setParameter("batchId", batch.getId())
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
                if (balance == null) em.persist(StockBalance.received(location, batch, preparedLine.accepted));
                else balance.receive(preparedLine.accepted);
                movements.add(StockMovement.receipt(location, batch, preparedLine.accepted, receipt.getId(), actor.userId()));
                poItem.recordReceived(preparedLine.accepted);
            }
            em.persist(GoodsReceiptItem.create(receipt, poItem, preparedLine.accepted, preparedLine.rejected,
                    batch, clean(preparedLine.request.rejectionReason)));
            receiptLineCount++;
        }
        receipt.post(actor.userId());
        em.flush(); // Persist the posted receipt and its item/batch/stock-balance context before adding ledger movements.
        for (StockMovement movement : movements) em.persist(movement);
        em.flush();

        boolean fullyReceived = order.getItems().stream().allMatch(item -> item.getReceivedQuantity().compareTo(item.getOrderedQuantity()) >= 0);
        if (fullyReceived) order.markReceived(); else order.markPartiallyReceived();
        em.flush();
        audit.recordAction("GOODS_RECEIPT_POSTED", "GOODS_RECEIPT", receipt.getId(), "goods receipt posted to stock ledger", order.getBranchId());
        audit.recordAction("PURCHASE_ORDER_RECEIPT_UPDATED", "PURCHASE_ORDER", order.getId(), "purchase order quantities updated", order.getBranchId());
        return GoodsReceiptResponse.from(receipt, receiptLineCount);
    }

    private MedicationBatch findOrCreateBatch(Medication medication, GoodsReceiptLineRequest line) {
        String batchNumber = requiredText(line.batchNumber, 80, "MEDICATION_BATCH_NUMBER_INVALID");
        MedicationBatch existing = em.createQuery("select b from MedicationBatch b where b.medication.id=:medicationId and b.batchNumber=:batchNumber", MedicationBatch.class)
                .setParameter("medicationId", medication.getId()).setParameter("batchNumber", batchNumber)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            if (!existing.getExpiresOn().equals(line.expiresOn)
                    || !Objects.equals(existing.getManufacturer(), clean(line.manufacturer))
                    || !Objects.equals(existing.getManufacturedOn(), line.manufacturedOn)) {
                throw BusinessException.conflict("MEDICATION_BATCH_DETAILS_MISMATCH", "This batch number already exists with different expiry or manufacturer details.");
            }
            return existing;
        }
        MedicationBatch batch = MedicationBatch.received(medication, batchNumber, clean(line.manufacturer), line.manufacturedOn, line.expiresOn);
        em.persist(batch);
        return batch;
    }

    private void validateBatchDetails(GoodsReceiptLineRequest line, HospitalBranch branch) {
        requiredText(line.batchNumber, 80, "MEDICATION_BATCH_NUMBER_INVALID");
        if (line.expiresOn == null || !line.expiresOn.isAfter(today(branch))) {
            throw BusinessException.badRequest("MEDICATION_BATCH_EXPIRY_INVALID", "Accepted medicine batches must have a future expiry date.");
        }
        if (line.manufacturedOn != null && line.expiresOn.isBefore(line.manufacturedOn)) {
            throw BusinessException.badRequest("MEDICATION_BATCH_DATES_INVALID", "Expiry date cannot be before manufacture date.");
        }
        if (line.manufacturer != null && line.manufacturer.length() > 160) {
            throw BusinessException.badRequest("MEDICATION_MANUFACTURER_INVALID", "Manufacturer name is too long.");
        }
    }

    private PurchaseOrder lockOrder(UUID orderId) {
        PurchaseOrder order = em.find(PurchaseOrder.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) throw BusinessException.notFound("Purchase order");
        order.getItems().size();
        return order;
    }

    private CurrentUser requireInventoryAccess(UUID branchId) {
        if (branchId == null) throw BusinessException.badRequest("BRANCH_REQUIRED", "branchId is required.");
        CurrentUser user = users.requireCurrentUser();
        users.requireBranchAccess(branchId);
        if (!user.roleCodes().stream().anyMatch(Set.of("HOSPITAL_ADMIN", "HOSPITAL_MANAGER", "INVENTORY_OFFICER")::contains)) {
            throw BusinessException.forbidden("PROCUREMENT_ROLE_REQUIRED", "A procurement or inventory role is required.");
        }
        return user;
    }

    private CurrentUser requireApproverAccess(UUID branchId) {
        CurrentUser user = users.requireCurrentUser();
        users.requireBranchAccess(branchId);
        if (!user.roleCodes().stream().anyMatch(Set.of("HOSPITAL_ADMIN", "HOSPITAL_MANAGER")::contains)) {
            throw BusinessException.forbidden("PURCHASE_ORDER_APPROVER_REQUIRED", "Only a hospital manager or administrator may approve purchase orders.");
        }
        return user;
    }

    private static LocalDate today(HospitalBranch branch) {
        try { return LocalDate.now(ZoneId.of(branch.getTimezone())); }
        catch (Exception ex) { return LocalDate.now(); }
    }
    private static String generatedNumber(String prefix, LocalDate businessDate) {
        return prefix + "-" + businessDate.toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
    private static int safeLimit(int limit, int max) { return Math.min(Math.max(limit, 1), max); }
    private static String normalize(String value) { return value == null ? "" : value.trim().replace("%", "").replace("_", ""); }
    private static String clean(String value) { if (value == null) return null; String v=value.trim(); return v.isEmpty()?null:v; }
    private static String requiredText(String value, int max, String code) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw BusinessException.badRequest(code, "A required text field is missing or too long.");
        return value.trim();
    }
    private static BigDecimal decimal(BigDecimal value, int scale, boolean positive, String code) {
        if (value == null || value.signum() < 0 || (positive && value.signum() <= 0)) throw BusinessException.badRequest(code, "A valid non-negative numeric value is required.");
        try { return value.setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw BusinessException.badRequest(code, "The numeric value has too many decimal places."); }
    }
    private record PreparedReceiptLine(PurchaseOrderItem poItem, BigDecimal accepted, BigDecimal rejected, GoodsReceiptLineRequest request) { }
}
