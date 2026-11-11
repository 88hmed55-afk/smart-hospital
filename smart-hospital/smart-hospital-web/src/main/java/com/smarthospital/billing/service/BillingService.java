package com.smarthospital.billing.service;

import com.smarthospital.audit.service.AuditService;
import com.smarthospital.accounting.service.AccountingService;
import com.smarthospital.billing.dto.InvoiceCreateRequest;
import com.smarthospital.billing.dto.ServiceCatalogResponse;
import com.smarthospital.billing.dto.BillableEncounterResponse;
import com.smarthospital.billing.dto.InvoiceItemCreateRequest;
import com.smarthospital.billing.dto.InvoiceResponse;
import com.smarthospital.billing.dto.PaymentCreateRequest;
import com.smarthospital.billing.entity.Invoice;
import com.smarthospital.billing.entity.InvoiceItem;
import com.smarthospital.billing.entity.InvoiceStatus;
import com.smarthospital.billing.entity.Payment;
import com.smarthospital.billing.entity.PaymentMethod;
import com.smarthospital.billing.entity.PaymentStatus;
import com.smarthospital.billing.entity.ServiceCatalog;
import com.smarthospital.clinical.entity.Encounter;
import com.smarthospital.clinical.entity.EncounterStatus;
import com.smarthospital.common.error.BusinessException;
import com.smarthospital.organization.entity.HospitalBranch;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class BillingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;

    @EJB
    private CurrentUserService users;

    @EJB
    private AuditService audit;

    @EJB
    private AccountingService accounting;
    public List<ServiceCatalogResponse> listActiveServices(UUID branchId, String query, int limit) {
        requireBillingActor(branchId);
        String normalized = query == null ? "" : query.trim().replace("%", "").replace("_", "");
        if (!normalized.isEmpty() && normalized.length() < 2) {
            throw BusinessException.badRequest("SERVICE_QUERY_TOO_SHORT", "Enter at least two characters or leave the search empty.");
        }
        if (normalized.length() > 80) {
            throw BusinessException.badRequest("SERVICE_QUERY_TOO_LONG", "The service search is limited to 80 characters.");
        }
        HospitalBranch branch = em.find(HospitalBranch.class, branchId);
        if (branch == null || !branch.isActive()) throw BusinessException.notFound("Active branch");
        return em.createQuery("select s from ServiceCatalog s where s.active=true and s.currencyCode=:currency "
                        + "and (:pattern is null or lower(s.code) like :pattern or lower(s.name) like :pattern) order by s.name", ServiceCatalog.class)
                .setParameter("currency", branch.getDefaultCurrency())
                .setParameter("pattern", normalized.isEmpty() ? null : "%" + normalized.toLowerCase(java.util.Locale.ROOT) + "%")
                .setMaxResults(Math.min(Math.max(limit, 1), 100))
                .getResultList().stream().map(ServiceCatalogResponse::from).toList();
    }
    public List<BillableEncounterResponse> listBillableEncounters(UUID branchId, String query, int limit) {
        requireBillingActor(branchId);
        String normalized = query == null ? "" : query.trim().replace("%", "").replace("_", "");
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw BusinessException.badRequest("ENCOUNTER_QUERY_INVALID", "Enter between 2 and 80 characters to search encounters.");
        }
        String pattern = "%" + normalized.toLowerCase(java.util.Locale.ROOT) + "%";
        return em.createQuery("select e from Encounter e join fetch e.patient p where e.branchId=:branch "
                        + "and e.status<>:cancelled and exists (select pba.id from PatientBranchAccess pba "
                        + "where pba.patient=e.patient and pba.branch.id=e.branchId and pba.revokedAt is null) "
                        + "and (lower(p.mrn) like :pattern or lower(p.firstName) like :pattern or lower(p.lastName) like :pattern) "
                        + "order by e.startedAt desc", Encounter.class)
                .setParameter("branch", branchId)
                .setParameter("cancelled", EncounterStatus.CANCELLED)
                .setParameter("pattern", pattern)
                .setMaxResults(Math.min(Math.max(limit, 1), 50))
                .getResultList().stream().map(BillableEncounterResponse::from).toList();
    }
    public InvoiceResponse createDraft(InvoiceCreateRequest request) {
        if (request == null || request.items == null || request.items.isEmpty()) {
            throw BusinessException.badRequest("INVOICE_ITEMS_REQUIRED", "At least one invoice item is required.");
        }
        if (request.items.size() > 40) {
            throw BusinessException.badRequest("INVOICE_ITEM_LIMIT_EXCEEDED", "An invoice can contain at most 40 service items.");
        }

        CurrentUser actor = requireBillingActor(request.branchId);
        HospitalBranch branch = em.find(HospitalBranch.class, request.branchId);
        if (branch == null || !branch.isActive()) {
            throw BusinessException.notFound("Active branch");
        }

        Encounter encounter = em.find(Encounter.class, request.encounterId, LockModeType.PESSIMISTIC_READ);
        if (encounter == null) {
            throw BusinessException.notFound("Encounter");
        }
        if (!encounter.getBranchId().equals(request.branchId)) {
            throw BusinessException.badRequest("INVOICE_BRANCH_MISMATCH", "Invoice and encounter must belong to the same branch.");
        }
        if (encounter.getStatus() == EncounterStatus.CANCELLED) {
            throw BusinessException.conflict("CANCELLED_ENCOUNTER_NOT_BILLABLE", "A cancelled encounter cannot be invoiced.");
        }
        users.requirePatientAccessInBranch(encounter.getPatientId(), request.branchId);

        Invoice invoice = Invoice.draft(
                request.branchId,
                generatedNumber("INV"),
                encounter.getPatient(),
                encounter,
                branch.getDefaultCurrency(),
                actor.userId());

        Set<UUID> seenServiceIds = new HashSet<>();
        for (InvoiceItemCreateRequest line : request.items) {
            if (line == null || line.serviceId == null || line.quantity == null
                    || line.quantity.compareTo(BigDecimal.ZERO) <= 0
                    || line.quantity.compareTo(new BigDecimal("1000")) > 0) {
                throw BusinessException.badRequest("INVOICE_ITEM_INVALID", "Each item requires a valid service and quantity.");
            }
            if (!seenServiceIds.add(line.serviceId)) {
                throw BusinessException.badRequest("DUPLICATE_INVOICE_SERVICE", "The same service must not appear twice; combine its quantity.");
            }

            ServiceCatalog service = em.find(ServiceCatalog.class, line.serviceId);
            if (service == null || !service.isActive()) {
                throw BusinessException.notFound("Active billable service");
            }
            if (!service.getCurrencyCode().equals(branch.getDefaultCurrency())) {
                throw BusinessException.conflict("SERVICE_CURRENCY_MISMATCH", "The service currency does not match the branch billing currency.");
            }

            BigDecimal quantity;
            try {
                quantity = line.quantity.setScale(3, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw BusinessException.badRequest("INVOICE_QUANTITY_PRECISION_INVALID", "Invoice quantity supports at most three decimal places.");
            }
            invoice.addItem(InvoiceItem.service(service, quantity));
        }

        em.persist(invoice);
        em.flush();
        audit.recordAction("INVOICE_DRAFT_CREATED", "INVOICE", invoice.getId(), "invoice draft created", request.branchId);
        return buildResponse(invoice);
    }
    public InvoiceResponse issue(UUID invoiceId) {
        Invoice invoice = em.find(Invoice.class, invoiceId, LockModeType.PESSIMISTIC_WRITE);
        if (invoice == null) {
            throw BusinessException.notFound("Invoice");
        }
        requireBillingActor(invoice.getBranchId());
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw BusinessException.conflict("INVOICE_NOT_DRAFT", "Only draft invoices can be issued.");
        }
        if (invoice.getItems().isEmpty()) {
            throw BusinessException.conflict("EMPTY_INVOICE", "An invoice must contain at least one item.");
        }

        invoice.recalculate();
        invoice.issue();
        em.flush();
        accounting.postInvoiceIssued(invoice);
        audit.recordAction("INVOICE_ISSUED", "INVOICE", invoice.getId(), "invoice issued", invoice.getBranchId());
        return buildResponse(invoice);
    }
    public InvoiceResponse recordPayment(UUID invoiceId, PaymentCreateRequest request) {
        if (request == null || request.amount == null || request.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("PAYMENT_AMOUNT_INVALID", "A positive payment amount is required.");
        }
        if (request.paymentMethod == null) {
            throw BusinessException.badRequest("PAYMENT_METHOD_REQUIRED", "A payment method is required.");
        }

        String idempotencyKey = request.idempotencyKey == null ? null : request.idempotencyKey.trim();
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 80) {
            throw BusinessException.badRequest("PAYMENT_IDEMPOTENCY_KEY_INVALID", "A 16 to 80 character idempotency key is required.");
        }

        BigDecimal amount;
        try {
            amount = request.amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw BusinessException.badRequest("PAYMENT_PRECISION_INVALID", "Payment amount supports at most two decimal places.");
        }

        String reference = clean(request.transactionReference);
        String fingerprint = paymentFingerprint(invoiceId, amount, request.paymentMethod, reference);

        // The invoice lock serializes concurrent payment attempts for the same invoice.
        Invoice invoice = em.find(Invoice.class, invoiceId, LockModeType.PESSIMISTIC_WRITE);
        if (invoice == null) {
            throw BusinessException.notFound("Invoice");
        }
        CurrentUser actor = requireBillingActor(invoice.getBranchId());

        Payment previous = em.createQuery(
                        "select p from Payment p join fetch p.invoice where p.idempotencyKey=:key",
                        Payment.class)
                .setParameter("key", idempotencyKey)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (previous != null) {
            if (!previous.getInvoiceId().equals(invoiceId)
                    || !fingerprint.equals(previous.getRequestFingerprint())) {
                throw BusinessException.conflict(
                        "PAYMENT_IDEMPOTENCY_KEY_REUSED",
                        "This payment idempotency key was already used with a different invoice or payment payload.");
            }
            return buildResponse(invoice);
        }

        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
            throw BusinessException.conflict("INVOICE_NOT_PAYABLE", "Only issued or partially paid invoices can receive payments.");
        }

        BigDecimal paidBefore = postedAmount(invoiceId);
        BigDecimal balance = invoice.getTotalAmount().subtract(paidBefore);
        if (amount.compareTo(balance) > 0) {
            throw BusinessException.conflict("PAYMENT_EXCEEDS_BALANCE", "The payment exceeds the outstanding invoice balance.");
        }

        String receipt = generatedNumber("RCT");
        Payment payment = Payment.post(
                invoice,
                receipt,
                amount,
                request.paymentMethod,
                reference,
                idempotencyKey,
                fingerprint,
                actor.userId());
        em.persist(payment);
        try {
            em.flush();
        } catch (PersistenceException exception) {
            if (hasSqlState(exception, "23505")) {
                throw BusinessException.conflict(
                        "PAYMENT_IDEMPOTENCY_CONFLICT",
                        "A payment with this idempotency key was already recorded. Retry with the same key and identical payload.");
            }
            throw exception;
        }

        // The payment journal is posted in the same JTA transaction as the receipt and invoice status.
        accounting.postPayment(invoice, payment);

        // Flush the payment row first; the invoice state trigger validates the posted-payment sum.
        invoice.applyPayment(paidBefore.add(amount));
        em.flush();
        audit.recordAction("INVOICE_PAYMENT_POSTED", "PAYMENT", payment.getId(), "payment posted; receipt=" + receipt, invoice.getBranchId());
        return buildResponse(invoice);
    }
    public InvoiceResponse find(UUID invoiceId) {
        Invoice invoice = em.find(Invoice.class, invoiceId);
        if (invoice == null) {
            throw BusinessException.notFound("Invoice");
        }
        requireBillingActor(invoice.getBranchId());
        audit.recordAction("INVOICE_VIEWED", "INVOICE", invoice.getId(), "invoice detail viewed", invoice.getBranchId());
        return buildResponse(invoice);
    }
    public List<InvoiceResponse> list(UUID branchId, String status, int limit) {
        requireBillingActor(branchId);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        InvoiceStatus requestedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                requestedStatus = InvoiceStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("INVOICE_STATUS_INVALID", "Unsupported invoice status.");
            }
        }

        String jpql = "select i from Invoice i where i.branchId=:branch ";
        if (requestedStatus != null) {
            jpql += "and i.status=:status ";
        }
        jpql += "order by i.createdAt desc";

        var query = em.createQuery(jpql, Invoice.class)
                .setParameter("branch", branchId)
                .setMaxResults(safeLimit);
        if (requestedStatus != null) {
            query.setParameter("status", requestedStatus);
        }
        return query.getResultList().stream().map(this::buildResponse).toList();
    }

    private CurrentUser requireBillingActor(UUID branchId) {
        if (branchId == null) {
            throw BusinessException.badRequest("BRANCH_REQUIRED", "A hospital branch is required.");
        }
        CurrentUser actor = users.requireCurrentUser();
        users.requireBranchAccess(branchId);

        if (actor.roleCodes().contains("HOSPITAL_ADMIN") || actor.roleCodes().contains("HOSPITAL_MANAGER")) {
            return actor;
        }
        if (!actor.roleCodes().contains("ACCOUNTANT")) {
            throw BusinessException.forbidden("BILLING_ROLE_REQUIRED", "An accountant or hospital manager role is required.");
        }

        StaffMember staff = em.find(StaffMember.class, users.requireStaffId());
        if (staff == null || !staff.isActive()
                || !staff.getBranch().getId().equals(branchId)
                || staff.getDepartment() == null
                || !staff.getDepartment().isActive()
                || !"FINANCE".equals(staff.getDepartment().getDepartmentType())) {
            throw BusinessException.forbidden("FINANCE_STAFF_SCOPE_REQUIRED", "The accountant must be an active finance staff member in this branch.");
        }
        return actor;
    }

    private String paymentFingerprint(UUID invoiceId, BigDecimal amount, PaymentMethod method, String reference) {
        String canonical = invoiceId + "|" + amount.toPlainString() + "|" + method.name() + "|" + (reference == null ? "" : reference);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    private boolean hasSqlState(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql && expected.equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BigDecimal postedAmount(UUID invoiceId) {
        return em.createQuery(
                        "select coalesce(sum(p.amount),0) from Payment p where p.invoice.id=:id and p.status=:status",
                        BigDecimal.class)
                .setParameter("id", invoiceId)
                .setParameter("status", PaymentStatus.POSTED)
                .getSingleResult()
                .setScale(2, RoundingMode.HALF_UP);
    }

    private InvoiceResponse buildResponse(Invoice invoice) {
        List<Payment> payments = em.createQuery(
                        "select p from Payment p where p.invoice.id=:id order by p.paidAt desc",
                        Payment.class)
                .setParameter("id", invoice.getId())
                .getResultList();
        return InvoiceResponse.from(invoice, postedAmount(invoice.getId()), payments);
    }

    private String generatedNumber(String prefix) {
        return prefix + "-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
