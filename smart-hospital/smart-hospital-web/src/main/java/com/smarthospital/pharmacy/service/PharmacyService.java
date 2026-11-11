package com.smarthospital.pharmacy.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.clinical.entity.EncounterStatus;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.entity.DoctorProfile;
import com.smarthospital.organization.entity.StaffMember;
import com.smarthospital.pharmacy.dto.DispenseCreateRequest;
import com.smarthospital.pharmacy.dto.DispenseItemRequest;
import com.smarthospital.pharmacy.dto.DispenseItemResponse;
import com.smarthospital.pharmacy.dto.DispenseResponse;
import com.smarthospital.pharmacy.dto.MedicationResponse;
import com.smarthospital.pharmacy.dto.PharmacyLocationResponse;
import com.smarthospital.pharmacy.dto.PrescriptionCreateRequest;
import com.smarthospital.pharmacy.dto.PrescriptionItemRequest;
import com.smarthospital.pharmacy.dto.PrescriptionItemResponse;
import com.smarthospital.pharmacy.dto.PrescriptionResponse;
import com.smarthospital.pharmacy.dto.StockAvailabilityResponse;
import com.smarthospital.pharmacy.entity.Dispense;
import com.smarthospital.pharmacy.entity.DispenseItem;
import com.smarthospital.pharmacy.entity.DispenseStatus;
import com.smarthospital.pharmacy.entity.InventoryLocation;
import com.smarthospital.pharmacy.entity.Medication;
import com.smarthospital.pharmacy.entity.MedicationBatch;
import com.smarthospital.pharmacy.entity.Prescription;
import com.smarthospital.pharmacy.entity.PrescriptionItem;
import com.smarthospital.pharmacy.entity.PrescriptionStatus;
import com.smarthospital.pharmacy.entity.StockBalance;
import com.smarthospital.pharmacy.entity.StockMovement;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class PharmacyService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService currentUserService;

    @EJB
    private AuditService auditService;

    @EJB
    private MedicationSafetyService medicationSafetyService;
    public List<MedicationResponse> searchMedications(String query, int limit) {
        currentUserService.requireCurrentUser();
        String normalized = query == null ? "" : query.trim().replace("%", "").replace("_", "");
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw BusinessException.badRequest("MEDICATION_QUERY_INVALID", "Enter between 2 and 80 searchable characters.");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        return em.createQuery(
                        "select m from Medication m where m.active=true "
                                + "and (lower(m.code) like :pattern or lower(m.genericName) like :pattern "
                                + "or lower(coalesce(m.brandName,'')) like :pattern) order by m.genericName",
                        Medication.class)
                .setParameter("pattern", pattern)
                .setMaxResults(safeLimit)
                .getResultList().stream().map(MedicationResponse::from).toList();
    }
    public PrescriptionResponse createPrescription(UUID encounterId, PrescriptionCreateRequest request) {
        if (request == null || request.items == null || request.items.isEmpty()) {
            throw BusinessException.badRequest("PRESCRIPTION_ITEMS_REQUIRED", "At least one prescription item is required.");
        }
        if (request.items.size() > 30) {
            throw BusinessException.badRequest("PRESCRIPTION_ITEM_LIMIT_EXCEEDED", "A prescription may contain no more than 30 items.");
        }
        UUID doctorStaffId = currentUserService.requireDoctorStaffId();
        Encounter encounter = requireEncounterForDoctor(encounterId, doctorStaffId, true);

        Set<UUID> medicationIds = new HashSet<>();
        for (PrescriptionItemRequest item : request.items) {
            if (item == null || item.medicationId == null || !medicationIds.add(item.medicationId)) {
                throw BusinessException.badRequest("PRESCRIPTION_MEDICATIONS_INVALID", "Medication identifiers must be present and unique.");
            }
            validateQuantity(item.quantityPrescribed, "PRESCRIPTION_QUANTITY_INVALID");
            if (item.dose == null || item.dose.isBlank() || item.dose.length() > 80
                    || item.frequency == null || item.frequency.isBlank() || item.frequency.length() > 100
                    || (item.route != null && item.route.length() > 60)
                    || (item.instructions != null && item.instructions.length() > 500)) {
                throw BusinessException.badRequest("PRESCRIPTION_INSTRUCTIONS_INVALID", "Dose, frequency, route or instructions are invalid.");
            }
            if (item.durationDays != null && item.durationDays <= 0) {
                throw BusinessException.badRequest("PRESCRIPTION_DURATION_INVALID", "Duration must be a positive number of days.");
            }
        }

        List<Medication> medications = em.createQuery(
                        "select m from Medication m where m.id in :ids and m.active=true",
                        Medication.class)
                .setParameter("ids", medicationIds)
                .getResultList();
        if (medications.size() != medicationIds.size()) {
            throw BusinessException.badRequest("MEDICATION_NOT_AVAILABLE", "One or more selected medications are missing or inactive.");
        }
        Map<UUID, Medication> medicationById = new HashMap<>();
        for (Medication medication : medications) {
            if (medication.isControlled()) {
                throw BusinessException.badRequest("CONTROLLED_MEDICATION_OUT_OF_SCOPE",
                        "Controlled medications are excluded from this academic implementation.");
            }
            medicationById.put(medication.getId(), medication);
        }

        DoctorProfile doctor = em.find(DoctorProfile.class, doctorStaffId);
        if (doctor == null) {
            throw BusinessException.forbidden("ACTIVE_DOCTOR_PROFILE_REQUIRED", "An active doctor profile is required to create a prescription.");
        }

        // Clinical safety screening runs before any write so a blocked prescription leaves no partial record.
        MedicationSafetyService.Assessment safety =
                medicationSafetyService.assess(encounter.getPatientId(), medications);
        String overrideReason = clean(request.overrideReason);
        if (safety.hasBlockers() && (overrideReason == null || overrideReason.length() < 10)) {
            String detail = safety.blockers().stream()
                    .map(MedicationSafetyService.SafetyWarning::message)
                    .collect(java.util.stream.Collectors.joining(" "));
            auditService.recordIndependentAction("MEDICATION_SAFETY_BLOCKED", "PRESCRIPTION", null, "DENIED",
                    truncate(detail, 900), encounter.getBranchId());
            throw BusinessException.conflict("MEDICATION_SAFETY_BLOCKED",
                    "Prescription blocked by medication safety screening. " + detail);
        }
        if (safety.hasBlockers()) {
            auditService.recordIndependentAction("MEDICATION_SAFETY_OVERRIDE", "PRESCRIPTION", null, "SUCCESS",
                    "Clinical override: " + overrideReason, encounter.getBranchId());
        } else if (!safety.warnings().isEmpty()) {
            String detail = safety.warnings().stream()
                    .map(MedicationSafetyService.SafetyWarning::message)
                    .collect(java.util.stream.Collectors.joining(" "));
            auditService.recordIndependentAction("MEDICATION_SAFETY_WARNING", "PRESCRIPTION", null, "SUCCESS",
                    truncate(detail, 900), encounter.getBranchId());
        }

        Prescription prescription = Prescription.create(encounter, doctor);
        em.persist(prescription);
        List<PrescriptionItem> createdItems = new ArrayList<>();
        for (PrescriptionItemRequest item : request.items) {
            PrescriptionItem prescriptionItem = PrescriptionItem.create(
                    prescription,
                    medicationById.get(item.medicationId),
                    item.dose.trim(),
                    clean(item.route),
                    item.frequency.trim(),
                    item.durationDays,
                    item.quantityPrescribed,
                    clean(item.instructions));
            em.persist(prescriptionItem);
            createdItems.add(prescriptionItem);
        }
        em.flush();
        auditService.recordAction("PRESCRIPTION_CREATED", "PRESCRIPTION", prescription.getId(),
                "prescription created", encounter.getBranchId());
        PrescriptionResponse response = toPrescriptionResponse(prescription, createdItems, Map.of());
        response.safetyWarnings = java.util.stream.Stream.concat(
                        safety.blockers().stream(), safety.warnings().stream())
                .map(MedicationSafetyService.SafetyWarning::message)
                .toList();
        return response;
    }
    public List<PrescriptionResponse> listEncounterPrescriptions(UUID encounterId) {
        UUID doctorStaffId = currentUserService.requireDoctorStaffId();
        Encounter encounter = requireEncounterForDoctor(encounterId, doctorStaffId, false);
        List<Prescription> prescriptions = em.createQuery(
                        "select p from Prescription p where p.encounter.id=:encounterId order by p.orderedAt desc",
                        Prescription.class)
                .setParameter("encounterId", encounter.getId())
                .setMaxResults(100)
                .getResultList();
        return mapPrescriptions(prescriptions);
    }
    public List<PrescriptionResponse> listBranchPrescriptions(UUID branchId, String status, int limit) {
        CurrentUser user = currentUserService.requireCurrentUser();
        currentUserService.requireBranchAccess(branchId);
        requirePharmacist(branchId, user);
        PrescriptionStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = PrescriptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("PRESCRIPTION_STATUS_INVALID", "The requested prescription status is not supported.");
            }
        }
        List<Prescription> prescriptions = em.createQuery(
                        "select p from Prescription p join fetch p.encounter e join fetch e.patient "
                                + "where e.branchId=:branchId "
                                + "and exists (select pba.id from PatientBranchAccess pba "
                                + "where pba.patient=e.patient and pba.branch.id=e.branchId and pba.revokedAt is null) "
                                + "and ((:status is null and p.status in (:activeStatus,:partialStatus)) or p.status=:status) "
                                + "order by p.orderedAt asc",
                        Prescription.class)
                .setParameter("branchId", branchId)
                .setParameter("status", statusFilter)
                .setParameter("activeStatus", PrescriptionStatus.ACTIVE)
                .setParameter("partialStatus", PrescriptionStatus.PARTIALLY_DISPENSED)
                .setMaxResults(Math.min(Math.max(limit, 1), 100))
                .getResultList();
        return mapPrescriptions(prescriptions);
    }
    public List<StockAvailabilityResponse> listStockAvailability(UUID branchId, String query, UUID locationId, int limit) {
        CurrentUser user = currentUserService.requireCurrentUser();
        currentUserService.requireBranchAccess(branchId);
        requirePharmacist(branchId, user);
        if (locationId != null) {
            InventoryLocation requestedLocation = em.find(InventoryLocation.class, locationId);
            if (requestedLocation == null || !requestedLocation.isActive()
                    || !"PHARMACY".equals(requestedLocation.getLocationType())
                    || !branchId.equals(requestedLocation.getBranchId())) {
                throw BusinessException.forbidden("PHARMACY_LOCATION_SCOPE_REQUIRED", "The requested location is not an active pharmacy location in the current branch.");
            }
        }
        String normalized = query == null ? "" : query.trim().replace("%", "").replace("_", "");
        if (!normalized.isEmpty() && (normalized.length() < 2 || normalized.length() > 80)) {
            throw BusinessException.badRequest("STOCK_QUERY_INVALID", "Enter between two and 80 characters or leave the search empty.");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<Object[]> rows = em.createQuery(
                        "select m.id, m.code, m.genericName, m.dispensingUnit, "
                                + "sum(sb.quantityOnHand - sb.quantityReserved) "
                                + "from StockBalance sb join sb.batch b join b.medication m join sb.location l "
                                + "where l.branch.id=:branchId and l.locationType='PHARMACY' and l.active=true "
                                + "and (:locationId is null or l.id=:locationId) "
                                + "and m.active=true and b.expiresOn>=:today "
                                + "and (:pattern is null or lower(m.code) like :pattern or lower(m.genericName) like :pattern) "
                                + "group by m.id, m.code, m.genericName, m.dispensingUnit "
                                + "having sum(sb.quantityOnHand - sb.quantityReserved)>0 order by m.genericName",
                        Object[].class)
                .setParameter("branchId", branchId)
                .setParameter("locationId", locationId)
                .setParameter("today", LocalDate.now())
                .setParameter("pattern", normalized.isEmpty() ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%")
                .setMaxResults(safeLimit)
                .getResultList();
        List<StockAvailabilityResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            StockAvailabilityResponse item = new StockAvailabilityResponse();
            item.medicationId = DbValues.uuid(row[0]);
            item.medicationCode = (String) row[1];
            item.medicationName = (String) row[2];
            item.dispensingUnit = (String) row[3];
            item.quantityAvailable = (BigDecimal) row[4];
            result.add(item);
        }
        return result;
    }
    public List<PharmacyLocationResponse> listPharmacyLocations(UUID branchId) {
        CurrentUser user = currentUserService.requireCurrentUser();
        currentUserService.requireBranchAccess(branchId);
        requirePharmacist(branchId, user);
        return em.createQuery(
                        "select l from InventoryLocation l where l.branch.id=:branchId "
                                + "and l.locationType='PHARMACY' and l.active=true order by l.code",
                        InventoryLocation.class)
                .setParameter("branchId", branchId)
                .getResultList().stream().map(PharmacyLocationResponse::from).toList();
    }
    public DispenseResponse getDispense(UUID dispenseId) {
        CurrentUser user = currentUserService.requireCurrentUser();
        UUID branchId = user.defaultBranchId();
        requirePharmacist(branchId, user);
        Dispense dispense = em.createQuery(
                        "select d from Dispense d join fetch d.prescription p "
                                + "join fetch p.encounter e join fetch e.patient "
                                + "join fetch d.inventoryLocation join fetch d.dispensedBy "
                                + "where d.id=:id and d.status=:status",
                        Dispense.class)
                .setParameter("id", dispenseId)
                .setParameter("status", DispenseStatus.POSTED)
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Dispense"));
        if (!branchId.equals(dispense.getBranchId())) {
            throw BusinessException.forbidden("DISPENSE_BRANCH_SCOPE_DENIED", "The dispense does not belong to the current branch.");
        }
        currentUserService.requirePatientAccessInBranch(dispense.getPrescription().getPatientId(), branchId);
        return toDispenseResponse(dispense);
    }
    public DispenseResponse dispense(UUID branchId, UUID prescriptionId, UUID locationId, String idempotencyKey,
                                     DispenseCreateRequest request) {
        validateIdempotencyKey(idempotencyKey);
        if (request == null || request.items == null || request.items.isEmpty() || request.items.size() > 30) {
            throw BusinessException.badRequest("DISPENSE_ITEMS_INVALID", "A dispense must contain between 1 and 30 items.");
        }
        Set<UUID> requestedItemIds = new HashSet<>();
        for (DispenseItemRequest item : request.items) {
            if (item == null || item.prescriptionItemId == null || !requestedItemIds.add(item.prescriptionItemId)) {
                throw BusinessException.badRequest("DISPENSE_ITEMS_INVALID", "Prescription item identifiers must be present and unique.");
            }
            validateQuantity(item.quantity, "DISPENSE_QUANTITY_INVALID");
        }

        CurrentUser user = currentUserService.requireCurrentUser();
        currentUserService.requireBranchAccess(branchId);
        StaffMember pharmacist = requirePharmacist(branchId, user);
        if (prescriptionId == null || locationId == null) {
            throw BusinessException.badRequest("DISPENSE_CONTEXT_REQUIRED", "Prescription and pharmacy location identifiers are required.");
        }
        InventoryLocation location = em.find(InventoryLocation.class, locationId);
        if (location == null || !location.isActive() || !"PHARMACY".equals(location.getLocationType())
                || !location.getBranchId().equals(branchId)) {
            throw BusinessException.forbidden("PHARMACY_LOCATION_SCOPE_REQUIRED", "An active pharmacy inventory location in the current branch is required.");
        }

        String fingerprint = fingerprint(prescriptionId, locationId, request.items);
        Dispense existing = findDispenseByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (!existing.getBranchId().equals(branchId)) {
                throw BusinessException.forbidden("DISPENSE_IDEMPOTENCY_SCOPE_DENIED", "The idempotency key belongs to a different branch.");
            }
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "This idempotency key was already used with a different dispense request.");
            }
            return toDispenseResponse(existing);
        }

        Prescription prescription = em.find(Prescription.class, prescriptionId, LockModeType.PESSIMISTIC_WRITE);
        if (prescription == null) throw BusinessException.notFound("Prescription");
        if (!prescription.getBranchId().equals(branchId)) {
            throw BusinessException.forbidden("PRESCRIPTION_BRANCH_SCOPE_DENIED", "The prescription does not belong to the current branch.");
        }
        currentUserService.requirePatientAccessInBranch(prescription.getPatientId(), branchId);
        if (prescription.getStatus() != PrescriptionStatus.ACTIVE
                && prescription.getStatus() != PrescriptionStatus.PARTIALLY_DISPENSED) {
            throw BusinessException.conflict("PRESCRIPTION_NOT_DISPENSABLE", "Only active or partially dispensed prescriptions may be dispensed.");
        }

        List<DispenseItemRequest> orderedRequests = request.items.stream()
                .sorted(Comparator.comparing(item -> item.prescriptionItemId.toString()))
                .toList();
        Map<UUID, PrescriptionItem> prescriptionItems = new HashMap<>();
        for (DispenseItemRequest itemRequest : orderedRequests) {
            PrescriptionItem item = em.find(PrescriptionItem.class, itemRequest.prescriptionItemId, LockModeType.PESSIMISTIC_WRITE);
            if (item == null || !item.getPrescriptionId().equals(prescriptionId)) {
                throw BusinessException.badRequest("PRESCRIPTION_ITEM_MISMATCH", "A selected item does not belong to this prescription.");
            }
            BigDecimal alreadyDispensed = dispensedQuantity(item.getId());
            BigDecimal remaining = item.getQuantityPrescribed().subtract(alreadyDispensed);
            if (itemRequest.quantity.compareTo(remaining) > 0) {
                throw BusinessException.conflict("PRESCRIPTION_QUANTITY_EXCEEDED", "The requested dispense quantity exceeds the remaining prescribed quantity.");
            }
            prescriptionItems.put(item.getId(), item);
        }

        Dispense dispense = Dispense.draft(prescription, pharmacist, location, idempotencyKey, fingerprint);
        em.persist(dispense);
        try {
            em.flush();
        } catch (PersistenceException exception) {
            if (hasSqlState(exception, "23505")) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_CONFLICT", "A request with this idempotency key is already being processed. Retry with the same key and identical payload.");
            }
            throw exception;
        }

        for (DispenseItemRequest itemRequest : orderedRequests) {
            PrescriptionItem prescriptionItem = prescriptionItems.get(itemRequest.prescriptionItemId);
            allocateFromFefoBatches(dispense, prescriptionItem, location, itemRequest.quantity, user.userId());
        }

        // Persist allocation rows and their stock movements while the dispense header is still DRAFT.
        // The database guard verifies every allocation before allowing the header to become POSTED.
        em.flush();
        dispense.post();
        em.flush();
        updatePrescriptionStatus(prescription);
        em.flush();
        auditService.recordAction("MEDICATION_DISPENSED", "DISPENSE", dispense.getId(),
                "prescription items dispensed using FEFO stock allocation", branchId);
        return toDispenseResponse(dispense);
    }

    private void allocateFromFefoBatches(Dispense dispense, PrescriptionItem prescriptionItem,
                                         InventoryLocation location, BigDecimal requested, UUID actorUserId) {
        List<StockBalance> balances = em.createQuery(
                        "select sb from StockBalance sb join fetch sb.batch b "
                                + "where sb.location.id=:locationId and b.medication.id=:medicationId "
                                + "and b.expiresOn>=:today and sb.quantityOnHand>sb.quantityReserved "
                                + "order by b.expiresOn asc, b.id asc",
                        StockBalance.class)
                .setParameter("locationId", location.getId())
                .setParameter("medicationId", prescriptionItem.getMedicationId())
                .setParameter("today", LocalDate.now())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        BigDecimal remaining = requested;
        for (StockBalance balance : balances) {
            if (remaining.signum() <= 0) break;
            BigDecimal available = balance.getAvailableQuantity();
            if (available.signum() <= 0) continue;
            BigDecimal allocated = available.min(remaining);
            try {
                balance.dispense(allocated);
            } catch (IllegalStateException exception) {
                throw BusinessException.conflict("STOCK_BALANCE_CHANGED", "The stock balance changed while dispensing. Retry the operation.");
            }
            em.flush();
            em.persist(DispenseItem.create(dispense, prescriptionItem, balance.getBatch(), allocated));
            em.persist(StockMovement.dispense(location, balance.getBatch(), allocated, dispense.getId(), actorUserId));
            remaining = remaining.subtract(allocated);
        }
        if (remaining.signum() > 0) {
            throw BusinessException.conflict("INSUFFICIENT_PHARMACY_STOCK", "The pharmacy does not have enough unexpired stock to fulfill the requested quantity.");
        }
    }

    private void updatePrescriptionStatus(Prescription prescription) {
        List<PrescriptionItem> items = findPrescriptionItems(prescription.getId());
        boolean allComplete = true;
        boolean anyDispensed = false;
        Map<UUID, BigDecimal> quantities = findDispensedQuantities(items);
        for (PrescriptionItem item : items) {
            BigDecimal dispensed = quantities.getOrDefault(item.getId(), ZERO);
            if (dispensed.signum() > 0) anyDispensed = true;
            if (dispensed.compareTo(item.getQuantityPrescribed()) < 0) allComplete = false;
        }
        prescription.updateStatus(allComplete ? PrescriptionStatus.DISPENSED
                : anyDispensed ? PrescriptionStatus.PARTIALLY_DISPENSED : PrescriptionStatus.ACTIVE);
    }

    private Encounter requireEncounterForDoctor(UUID encounterId, UUID doctorStaffId, boolean lock) {
        Encounter encounter = lock
                ? em.find(Encounter.class, encounterId, LockModeType.PESSIMISTIC_WRITE)
                : em.find(Encounter.class, encounterId);
        if (encounter == null) throw BusinessException.notFound("Encounter");
        currentUserService.requireBranchAccess(encounter.getBranchId());
        currentUserService.requirePatientAccessInBranch(encounter.getPatientId(), encounter.getBranchId());
        if (!encounter.getAttendingDoctorId().equals(doctorStaffId)) {
            throw BusinessException.forbidden("ATTENDING_DOCTOR_REQUIRED", "Only the attending doctor may create or view prescriptions for this encounter.");
        }
        if (lock && encounter.getStatus() != EncounterStatus.OPEN && encounter.getStatus() != EncounterStatus.IN_PROGRESS) {
            throw BusinessException.conflict("ENCOUNTER_NOT_ACTIVE", "Prescriptions cannot be added after the encounter is completed.");
        }
        return encounter;
    }

    private StaffMember requirePharmacist(UUID branchId, CurrentUser user) {
        currentUserService.requireBranchAccess(branchId);
        if (!user.roleCodes().contains("PHARMACIST")) {
            throw BusinessException.forbidden("PHARMACIST_ROLE_REQUIRED", "A pharmacist role is required for this operation.");
        }
        StaffMember staff = em.find(StaffMember.class, currentUserService.requireStaffId());
        if (staff == null || !staff.isActive() || staff.getDepartment() == null
                || !staff.getDepartment().isActive()
                || !staff.getBranch().getId().equals(branchId)
                || !staff.getDepartment().getBranch().getId().equals(branchId)
                || !"PHARMACY".equals(staff.getDepartment().getDepartmentType())) {
            throw BusinessException.forbidden("PHARMACIST_STAFF_SCOPE_REQUIRED", "The user must be an active pharmacist in the pharmacy department of this branch.");
        }
        return staff;
    }

    private List<PrescriptionResponse> mapPrescriptions(List<Prescription> prescriptions) {
        if (prescriptions == null || prescriptions.isEmpty()) return List.of();
        List<PrescriptionItem> allItems = findPrescriptionItemsForPrescriptions(prescriptions);
        Map<UUID, BigDecimal> dispensed = findDispensedQuantities(allItems);
        Map<UUID, List<PrescriptionItemResponse>> byPrescription = new HashMap<>();
        for (PrescriptionItem item : allItems) {
            byPrescription.computeIfAbsent(item.getPrescriptionId(), ignored -> new ArrayList<>())
                    .add(toPrescriptionItemResponse(item, dispensed.getOrDefault(item.getId(), ZERO)));
        }
        return prescriptions.stream()
                .map(prescription -> PrescriptionResponse.from(prescription,
                        byPrescription.getOrDefault(prescription.getId(), List.of())))
                .toList();
    }

    private PrescriptionResponse toPrescriptionResponse(Prescription prescription,
                                                        List<PrescriptionItem> items,
                                                        Map<UUID, BigDecimal> dispensed) {
        List<PrescriptionItemResponse> responseItems = items.stream()
                .map(item -> toPrescriptionItemResponse(item, dispensed.getOrDefault(item.getId(), ZERO)))
                .toList();
        return PrescriptionResponse.from(prescription, responseItems);
    }

    private PrescriptionItemResponse toPrescriptionItemResponse(PrescriptionItem item, BigDecimal dispensed) {
        Medication medication = item.getMedication();
        PrescriptionItemResponse response = new PrescriptionItemResponse();
        response.id = item.getId();
        response.medicationId = medication.getId();
        response.medicationCode = medication.getCode();
        response.medicationName = medication.getGenericName();
        response.brandName = medication.getBrandName();
        response.strength = medication.getStrength();
        response.dosageForm = medication.getDosageForm();
        response.dispensingUnit = medication.getDispensingUnit();
        response.dose = item.getDose();
        response.route = item.getRoute();
        response.frequency = item.getFrequency();
        response.durationDays = item.getDurationDays();
        response.quantityPrescribed = item.getQuantityPrescribed();
        response.quantityDispensed = dispensed;
        response.quantityRemaining = item.getQuantityPrescribed().subtract(dispensed);
        response.instructions = item.getInstructions();
        return response;
    }

    private List<PrescriptionItem> findPrescriptionItems(UUID prescriptionId) {
        return em.createQuery(
                        "select i from PrescriptionItem i join fetch i.medication "
                                + "where i.prescription.id=:prescriptionId order by i.id",
                        PrescriptionItem.class)
                .setParameter("prescriptionId", prescriptionId)
                .getResultList();
    }

    private List<PrescriptionItem> findPrescriptionItemsForPrescriptions(List<Prescription> prescriptions) {
        List<UUID> ids = prescriptions.stream().map(Prescription::getId).toList();
        return em.createQuery(
                        "select i from PrescriptionItem i join fetch i.medication "
                                + "where i.prescription.id in :ids order by i.prescription.id, i.id",
                        PrescriptionItem.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    private Map<UUID, BigDecimal> findDispensedQuantities(List<PrescriptionItem> items) {
        if (items == null || items.isEmpty()) return Map.of();
        List<UUID> ids = items.stream().map(PrescriptionItem::getId).toList();
        List<Object[]> rows = em.createQuery(
                        "select di.prescriptionItem.id, sum(di.quantityDispensed) "
                                + "from DispenseItem di where di.prescriptionItem.id in :ids "
                                + "and di.dispense.status=:posted group by di.prescriptionItem.id",
                        Object[].class)
                .setParameter("ids", ids)
                .setParameter("posted", DispenseStatus.POSTED)
                .getResultList();
        Map<UUID, BigDecimal> quantities = new HashMap<>();
        for (Object[] row : rows) quantities.put(DbValues.uuid(row[0]), (BigDecimal) row[1]);
        return quantities;
    }

    private BigDecimal dispensedQuantity(UUID prescriptionItemId) {
        BigDecimal total = em.createQuery(
                        "select sum(di.quantityDispensed) from DispenseItem di "
                                + "where di.prescriptionItem.id=:itemId and di.dispense.status=:posted",
                        BigDecimal.class)
                .setParameter("itemId", prescriptionItemId)
                .setParameter("posted", DispenseStatus.POSTED)
                .getSingleResult();
        return total == null ? ZERO : total;
    }

    private Dispense findDispenseByIdempotencyKey(String key) {
        List<Dispense> results = em.createQuery(
                        "select d from Dispense d join fetch d.prescription join fetch d.inventoryLocation join fetch d.dispensedBy "
                                + "where d.idempotencyKey=:key",
                        Dispense.class)
                .setParameter("key", key)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private DispenseResponse toDispenseResponse(Dispense dispense) {
        List<DispenseItem> rows = em.createQuery(
                        "select di from DispenseItem di "
                                + "join fetch di.prescriptionItem pi join fetch pi.medication m join fetch di.batch b "
                                + "where di.dispense.id=:dispenseId order by pi.id, b.expiresOn",
                        DispenseItem.class)
                .setParameter("dispenseId", dispense.getId())
                .getResultList();
        List<DispenseItemResponse> items = new ArrayList<>();
        for (DispenseItem dispenseItem : rows) {
            PrescriptionItem prescriptionItem = dispenseItem.getPrescriptionItem();
            Medication medication = prescriptionItem.getMedication();
            MedicationBatch batch = dispenseItem.getBatch();
            DispenseItemResponse response = new DispenseItemResponse();
            response.id = dispenseItem.getId();
            response.prescriptionItemId = prescriptionItem.getId();
            response.medicationId = medication.getId();
            response.medicationCode = medication.getCode();
            response.medicationName = medication.getGenericName();
            response.batchId = batch.getId();
            response.batchNumber = batch.getBatchNumber();
            response.expiresOn = batch.getExpiresOn();
            response.quantityDispensed = dispenseItem.getQuantityDispensed();
            items.add(response);
        }
        return DispenseResponse.from(dispense, items);
    }

    private boolean hasSqlState(Throwable throwable, String expectedState) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.sql.SQLException sqlException
                    && expectedState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void validateQuantity(BigDecimal value, String code) {
        if (value == null || value.signum() <= 0) {
            throw BusinessException.badRequest(code, "Quantity must be greater than zero.");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (Math.max(normalized.scale(), 0) > 3
                || Math.max(normalized.precision() - normalized.scale(), 0) > 11
                || normalized.precision() > 14) {
            throw BusinessException.badRequest(code, "Quantity must fit NUMERIC(14,3).");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{16,80}")) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Provide an Idempotency-Key containing 16–80 safe characters.");
        }
    }

    private String fingerprint(UUID prescriptionId, UUID locationId, List<DispenseItemRequest> items) {
        String canonical = prescriptionId + "|" + locationId + "|" + items.stream()
                .sorted(Comparator.comparing(item -> item.prescriptionItemId.toString()))
                .map(item -> item.prescriptionItemId + ":" + item.quantity.stripTrailingZeros().toPlainString())
                .reduce((left, right) -> left + "|" + right).orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in the Java runtime.", exception);
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
