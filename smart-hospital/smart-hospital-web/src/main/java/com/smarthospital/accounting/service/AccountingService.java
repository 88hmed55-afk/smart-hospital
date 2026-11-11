package com.smarthospital.accounting.service;

import com.smarthospital.common.persistence.DbValues;

import com.smarthospital.accounting.dto.AccountResponse;
import com.smarthospital.accounting.dto.JournalEntryResponse;
import com.smarthospital.accounting.dto.ManualJournalCreateRequest;
import com.smarthospital.accounting.dto.TrialBalanceResponse;
import com.smarthospital.accounting.entity.ChartOfAccount;
import com.smarthospital.accounting.entity.FiscalPeriod;
import com.smarthospital.accounting.entity.JournalEntry;
import com.smarthospital.accounting.entity.JournalLine;
import com.smarthospital.accounting.entity.JournalStatus;
import com.smarthospital.audit.service.AuditService;
import com.smarthospital.billing.entity.Invoice;
import com.smarthospital.billing.entity.Payment;
import com.smarthospital.billing.entity.PaymentMethod;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Double-entry accounting services. Source transactions call the posting helpers
 * through an EJB proxy so journal creation joins the caller's JTA transaction.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AccountingService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @PersistenceContext(unitName = "SmartHospitalPU")
    private EntityManager em;
    @EJB private CurrentUserService users;
    @EJB private AuditService audit;
    public List<AccountResponse> listAccounts(UUID branchId) {
        requireAccountingActor(branchId);
        return em.createQuery("select a from ChartOfAccount a where a.branchId=:branch and a.active=true order by a.accountCode", ChartOfAccount.class)
                .setParameter("branch", branchId)
                .getResultList().stream().map(this::accountResponse).toList();
    }
    public JournalEntryResponse findEntry(UUID entryId) {
        JournalEntry entry = em.find(JournalEntry.class, entryId);
        if (entry == null) throw BusinessException.notFound("Journal entry");
        requireAccountingActor(entry.getBranchId());
        audit.recordAction("JOURNAL_VIEWED", "JOURNAL_ENTRY", entry.getId(), "journal detail viewed", entry.getBranchId());
        return buildEntryResponses(List.of(entry)).get(0);
    }
    public List<JournalEntryResponse> listEntries(UUID branchId, LocalDate fromDate, LocalDate toDate, int limit) {
        requireAccountingActor(branchId);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw BusinessException.badRequest("JOURNAL_DATE_RANGE_INVALID", "The start date cannot be after the end date.");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String jpql = "select j from JournalEntry j where j.branchId=:branch ";
        if (fromDate != null) jpql += "and j.entryDate>=:fromDate ";
        if (toDate != null) jpql += "and j.entryDate<=:toDate ";
        jpql += "order by j.entryDate desc, j.createdAt desc";
        var query = em.createQuery(jpql, JournalEntry.class).setParameter("branch", branchId).setMaxResults(safeLimit);
        if (fromDate != null) query.setParameter("fromDate", fromDate);
        if (toDate != null) query.setParameter("toDate", toDate);
        return buildEntryResponses(query.getResultList());
    }
    public TrialBalanceResponse trialBalance(UUID branchId, LocalDate asOfDate) {
        requireAccountingActor(branchId);
        if (asOfDate == null) asOfDate = LocalDate.now(branchZone(branchId));
        HospitalBranch branch = em.find(HospitalBranch.class, branchId);
        if (branch == null || !branch.isActive()) throw BusinessException.notFound("Active branch");

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT a.id, a.account_code, a.account_name, a.account_type, a.normal_balance,
                       COALESCE(SUM(CASE WHEN je.id IS NOT NULL THEN jl.debit_amount ELSE 0 END), 0) AS debit_total,
                       COALESCE(SUM(CASE WHEN je.id IS NOT NULL THEN jl.credit_amount ELSE 0 END), 0) AS credit_total
                  FROM hospital.chart_of_accounts a
                  LEFT JOIN hospital.journal_lines jl
                    ON jl.account_id = a.id AND jl.branch_id = a.branch_id
                  LEFT JOIN hospital.journal_entries je
                    ON je.id = jl.journal_entry_id AND je.branch_id = jl.branch_id
                   AND je.entry_status = 'POSTED' AND je.entry_date <= ?1
                 WHERE a.branch_id = ?2
                 GROUP BY a.id, a.account_code, a.account_name, a.account_type, a.normal_balance
                 ORDER BY a.account_code
                """)
                .setParameter(2, branchId).setParameter(1, asOfDate).getResultList();

        TrialBalanceResponse response = new TrialBalanceResponse();
        response.branchId = branchId; response.asOfDate = asOfDate; response.currencyCode = branch.getDefaultCurrency();
        response.accounts = new ArrayList<>();
        response.totalDebits = ZERO; response.totalCredits = ZERO;
        response.totalDebitBalances = ZERO; response.totalCreditBalances = ZERO;
        for (Object[] row : rows) {
            BigDecimal debit = money(row[5]);
            BigDecimal credit = money(row[6]);
            BigDecimal net = debit.subtract(credit);
            TrialBalanceResponse.AccountBalance line = new TrialBalanceResponse.AccountBalance();
            line.accountId = DbValues.uuid(row[0]); line.accountCode = (String) row[1]; line.accountName = (String) row[2];
            line.accountType = (String) row[3]; line.normalBalance = (String) row[4];
            line.debitMovement = debit; line.creditMovement = credit;
            line.closingDebit = net.max(ZERO); line.closingCredit = net.negate().max(ZERO);
            response.accounts.add(line);
            response.totalDebits = response.totalDebits.add(debit);
            response.totalCredits = response.totalCredits.add(credit);
            response.totalDebitBalances = response.totalDebitBalances.add(line.closingDebit);
            response.totalCreditBalances = response.totalCreditBalances.add(line.closingCredit);
        }
        response.balanced = response.totalDebits.compareTo(response.totalCredits) == 0
                && response.totalDebitBalances.compareTo(response.totalCreditBalances) == 0;
        audit.recordAction("TRIAL_BALANCE_VIEWED", "ACCOUNTING_REPORT", null, "asOf=" + asOfDate, branchId);
        return response;
    }
    public JournalEntryResponse createManualDraft(ManualJournalCreateRequest request) {
        if (request == null || request.branchId == null || request.entryDate == null
                || request.description == null || request.description.isBlank()
                || request.lines == null || request.lines.size() < 2 || request.lines.size() > 50) {
            throw BusinessException.badRequest("MANUAL_JOURNAL_REQUEST_INVALID", "Branch, date, description and between 2 and 50 journal lines are required.");
        }
        CurrentUser actor = requireAccountingActor(request.branchId);
        FiscalPeriod period = openPeriod(request.branchId, request.entryDate);
        List<PreparedLine> lines = prepareLines(request.branchId, request.lines);
        BigDecimal debits = lines.stream().map(PreparedLine::debit).reduce(ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream().map(PreparedLine::credit).reduce(ZERO, BigDecimal::add);
        if (debits.signum() <= 0 || debits.compareTo(credits) != 0) {
            throw BusinessException.badRequest("JOURNAL_NOT_BALANCED", "Total debit and credit amounts must be equal and greater than zero.");
        }
        JournalEntry entry = JournalEntry.draft(request.branchId, period.getId(), journalNumber(request.branchId, request.entryDate),
                request.entryDate, request.description.trim(), "MANUAL", null, null, actor.userId());
        em.persist(entry);
        em.flush();
        persistLines(entry, request.branchId, lines);
        em.flush();
        audit.recordAction("JOURNAL_DRAFT_CREATED", "JOURNAL_ENTRY", entry.getId(), "manual journal draft created", request.branchId);
        return buildEntryResponses(List.of(entry)).get(0);
    }
    public JournalEntryResponse postEntry(UUID entryId) {
        JournalEntry entry = em.find(JournalEntry.class, entryId, LockModeType.PESSIMISTIC_WRITE);
        if (entry == null) throw BusinessException.notFound("Journal entry");
        CurrentUser actor = requireAccountingActor(entry.getBranchId());
        if (entry.getEntryStatus() != JournalStatus.DRAFT) {
            throw BusinessException.conflict("JOURNAL_NOT_DRAFT", "Only draft journal entries can be posted.");
        }
        openPeriod(entry.getBranchId(), entry.getEntryDate());
        List<JournalLine> lines = em.createQuery("select l from JournalLine l where l.journalEntryId=:id and l.branchId=:branch order by l.lineNumber", JournalLine.class)
                .setParameter("id", entryId).setParameter("branch", entry.getBranchId()).getResultList();
        validatePersistedLines(entry.getBranchId(), lines);
        entry.post(actor.userId());
        em.flush(); // MySQL trigger independently validates balance and open-period membership.
        audit.recordAction("JOURNAL_POSTED", "JOURNAL_ENTRY", entry.getId(), "journal posted", entry.getBranchId());
        return buildEntryResponses(List.of(entry)).get(0);
    }
    public JournalEntryResponse createReversalDraft(UUID originalEntryId) {
        JournalEntry original = em.find(JournalEntry.class, originalEntryId, LockModeType.PESSIMISTIC_WRITE);
        if (original == null) throw BusinessException.notFound("Original journal entry");
        CurrentUser actor = requireAccountingActor(original.getBranchId());
        if (original.getEntryStatus() != JournalStatus.POSTED) {
            throw BusinessException.conflict("REVERSAL_REQUIRES_POSTED_ENTRY", "Only a posted journal entry can be reversed.");
        }
        Long existing = em.createQuery("select count(j) from JournalEntry j where j.reversalOfEntryId=:original", Long.class)
                .setParameter("original", originalEntryId).getSingleResult();
        if (existing > 0) throw BusinessException.conflict("JOURNAL_ALREADY_REVERSED", "This journal entry already has a reversal entry.");
        LocalDate reversalDate = LocalDate.now(branchZone(original.getBranchId()));
        FiscalPeriod period = openPeriod(original.getBranchId(), reversalDate);
        List<JournalLine> originals = em.createQuery("select l from JournalLine l where l.journalEntryId=:id order by l.lineNumber", JournalLine.class)
                .setParameter("id", originalEntryId).getResultList();
        if (originals.size() < 2) throw BusinessException.conflict("JOURNAL_LINES_MISSING", "The original journal does not contain enough lines to reverse.");
        JournalEntry reversal = JournalEntry.draft(original.getBranchId(), period.getId(), journalNumber(original.getBranchId(), reversalDate),
                reversalDate, "Reversal of " + original.getEntryNumber() + " — " + original.getDescription(),
                "MANUAL_REVERSAL", original.getId(), original.getId(), actor.userId());
        em.persist(reversal); em.flush();
        List<PreparedLine> lines = new ArrayList<>();
        for (JournalLine line : originals) {
            ChartOfAccount account = em.find(ChartOfAccount.class, line.getAccountId());
            if (account == null || !account.getBranchId().equals(original.getBranchId()) || !account.isActive() || !account.isAllowsPosting() || account.isControlAccount()) throw BusinessException.conflict("REVERSAL_ACCOUNT_UNAVAILABLE", "An original journal account is inactive or no longer available for posting in this branch.");
            lines.add(new PreparedLine(account, money(line.getCreditAmount()), money(line.getDebitAmount()), "Reversal: " + safeMemo(line.getLineMemo())));
        }
        persistLines(reversal, original.getBranchId(), lines); em.flush();
        audit.recordAction("JOURNAL_REVERSAL_DRAFT_CREATED", "JOURNAL_ENTRY", reversal.getId(), "reversal draft created", original.getBranchId());
        return buildEntryResponses(List.of(reversal)).get(0);
    }

    /** Called by BillingService after an invoice is issued; joins its active JTA transaction. */
    public void postInvoiceIssued(Invoice invoice) {
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount().signum() == 0) return;
        postSourceEntry(invoice.getBranchId(), invoice.getIssuedAt() == null ? LocalDate.now(branchZone(invoice.getBranchId())) : invoice.getIssuedAt().atZone(branchZone(invoice.getBranchId())).toLocalDate(),
                "Invoice " + invoice.getInvoiceNumber(), "BILLING_INVOICE", invoice.getId(),
                invoiceLines(invoice));
    }

    /** Called by BillingService after a payment row is persisted; joins its active JTA transaction. */
    public void postPayment(Invoice invoice, Payment payment) {
        String debitAccount = payment.getPaymentMethod() == PaymentMethod.CASH ? "1000" : "1010";
        List<PreparedLine> lines = List.of(
                new PreparedLine(requirePostingAccount(invoice.getBranchId(), debitAccount), money(payment.getAmount()), ZERO, "Receipt " + payment.getReceiptNumber()),
                new PreparedLine(requirePostingAccount(invoice.getBranchId(), "1100"), ZERO, money(payment.getAmount()), "Reduce receivable for " + invoice.getInvoiceNumber())
        );
        postSourceEntry(invoice.getBranchId(), payment.getPaidAt().atZone(branchZone(invoice.getBranchId())).toLocalDate(),
                "Payment receipt " + payment.getReceiptNumber(), "BILLING_PAYMENT", payment.getId(), lines);
    }

    private List<PreparedLine> invoiceLines(Invoice invoice) {
        List<PreparedLine> lines = new ArrayList<>();
        lines.add(new PreparedLine(requirePostingAccount(invoice.getBranchId(), "1100"), money(invoice.getTotalAmount()), ZERO, "Receivable " + invoice.getInvoiceNumber()));
        if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().signum() > 0) {
            lines.add(new PreparedLine(requirePostingAccount(invoice.getBranchId(), "4200"), money(invoice.getDiscountAmount()), ZERO, "Discount " + invoice.getInvoiceNumber()));
        }
        if (invoice.getSubtotal() != null && invoice.getSubtotal().signum() > 0) {
            lines.add(new PreparedLine(requirePostingAccount(invoice.getBranchId(), "4100"), ZERO, money(invoice.getSubtotal()), "Service revenue " + invoice.getInvoiceNumber()));
        }
        if (invoice.getTaxAmount() != null && invoice.getTaxAmount().signum() > 0) {
            lines.add(new PreparedLine(requirePostingAccount(invoice.getBranchId(), "2200"), ZERO, money(invoice.getTaxAmount()), "Tax payable " + invoice.getInvoiceNumber()));
        }
        return lines;
    }

    private void postSourceEntry(UUID branchId, LocalDate date, String description, String sourceModule,
                                 UUID sourceId, List<PreparedLine> lines) {
        CurrentUser actor = requireAccountingActor(branchId);
        JournalEntry existing = em.createQuery("select j from JournalEntry j where j.branchId=:branch and j.sourceModule=:source and j.sourceReferenceId=:reference and j.reversalOfEntryId is null", JournalEntry.class)
                .setParameter("branch", branchId).setParameter("source", sourceModule).setParameter("reference", sourceId)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            if (existing.getEntryStatus() == JournalStatus.POSTED) return;
            throw BusinessException.conflict("SOURCE_JOURNAL_ALREADY_EXISTS", "A non-posted journal already exists for this source transaction.");
        }
        FiscalPeriod period = openPeriod(branchId, date);
        BigDecimal debit = lines.stream().map(PreparedLine::debit).reduce(ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(PreparedLine::credit).reduce(ZERO, BigDecimal::add);
        if (lines.size() < 2 || debit.signum() <= 0 || debit.compareTo(credit) != 0) {
            throw BusinessException.conflict("AUTOMATIC_JOURNAL_NOT_BALANCED", "The source transaction cannot be posted because its generated journal is not balanced.");
        }
        JournalEntry entry = JournalEntry.draft(branchId, period.getId(), journalNumber(branchId, date), date,
                description, sourceModule, sourceId, null, actor.userId());
        em.persist(entry); em.flush();
        persistLines(entry, branchId, lines); em.flush();
        entry.post(actor.userId()); em.flush();
        audit.recordAction("SOURCE_JOURNAL_POSTED", "JOURNAL_ENTRY", entry.getId(), "source=" + sourceModule, branchId);
    }

    private List<PreparedLine> prepareLines(UUID branchId, List<ManualJournalCreateRequest.Line> requests) {
        List<PreparedLine> result = new ArrayList<>();
        for (ManualJournalCreateRequest.Line line : requests) {
            if (line == null || line.accountCode == null || line.accountCode.isBlank()) throw BusinessException.badRequest("JOURNAL_LINE_INVALID", "Each journal line requires an account code.");
            BigDecimal debit = normalizeAmount(line.debitAmount, "debitAmount");
            BigDecimal credit = normalizeAmount(line.creditAmount, "creditAmount");
            boolean debitSide = debit.signum() > 0 && credit.signum() == 0;
            boolean creditSide = credit.signum() > 0 && debit.signum() == 0;
            if (!debitSide && !creditSide) throw BusinessException.badRequest("JOURNAL_LINE_SIDE_INVALID", "Each journal line must contain a positive debit or a positive credit, but not both.");
            ChartOfAccount account = requirePostingAccount(branchId, line.accountCode.trim());
            result.add(new PreparedLine(account, debit, credit, safeMemo(line.memo)));
        }
        return result;
    }

    private void validatePersistedLines(UUID branchId, List<JournalLine> lines) {
        if (lines.size() < 2) throw BusinessException.conflict("JOURNAL_MINIMUM_LINES", "A journal entry requires at least two lines.");
        BigDecimal debit = ZERO, credit = ZERO;
        for (JournalLine line : lines) {
            ChartOfAccount account = em.find(ChartOfAccount.class, line.getAccountId());
            if (account == null || !account.getBranchId().equals(branchId) || !account.isActive() || !account.isAllowsPosting() || account.isControlAccount()) {
                throw BusinessException.conflict("JOURNAL_ACCOUNT_NOT_POSTABLE", "A journal line refers to an inactive or non-postable account.");
            }
            debit = debit.add(money(line.getDebitAmount())); credit = credit.add(money(line.getCreditAmount()));
        }
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0) throw BusinessException.conflict("JOURNAL_NOT_BALANCED", "Journal debits and credits must be equal and greater than zero.");
    }

    private void persistLines(JournalEntry entry, UUID branchId, List<PreparedLine> lines) {
        short number = 1;
        for (PreparedLine line : lines) {
            em.persist(JournalLine.create(entry.getId(), branchId, number++, line.account().getId(), line.debit(), line.credit(), line.memo()));
        }
    }

    private FiscalPeriod openPeriod(UUID branchId, LocalDate date) {
        return em.createQuery("select p from FiscalPeriod p where p.branchId=:branch and p.periodStatus='OPEN' and p.startsOn<=:date and p.endsOn>:date", FiscalPeriod.class)
                .setParameter("branch", branchId).setParameter("date", date).setMaxResults(1)
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.conflict("OPEN_FISCAL_PERIOD_REQUIRED", "No open fiscal period covers the journal date for this branch."));
    }

    private ChartOfAccount requirePostingAccount(UUID branchId, String code) {
        ChartOfAccount account = em.createQuery("select a from ChartOfAccount a where a.branchId=:branch and a.accountCode=:code", ChartOfAccount.class)
                .setParameter("branch", branchId).setParameter("code", code).getResultStream().findFirst().orElse(null);
        if (account == null || !account.isActive() || !account.isAllowsPosting() || account.isControlAccount()) {
            throw BusinessException.conflict("POSTING_ACCOUNT_UNAVAILABLE", "Required posting account " + code + " is missing, inactive, or not enabled for posting.");
        }
        return account;
    }

    private CurrentUser requireAccountingActor(UUID branchId) {
        if (branchId == null) throw BusinessException.badRequest("BRANCH_REQUIRED", "A hospital branch is required.");
        CurrentUser actor = users.requireCurrentUser(); users.requireBranchAccess(branchId);
        if (actor.roleCodes().contains("HOSPITAL_ADMIN") || actor.roleCodes().contains("HOSPITAL_MANAGER")) return actor;
        if (!actor.roleCodes().contains("ACCOUNTANT")) throw BusinessException.forbidden("ACCOUNTING_ROLE_REQUIRED", "An accountant or hospital manager role is required.");
        StaffMember staff = em.find(StaffMember.class, users.requireStaffId());
        if (staff == null || !staff.isActive() || !staff.getBranch().getId().equals(branchId)
                || staff.getDepartment() == null || !staff.getDepartment().isActive()
                || !"FINANCE".equals(staff.getDepartment().getDepartmentType())) {
            throw BusinessException.forbidden("FINANCE_STAFF_SCOPE_REQUIRED", "The accountant must be an active finance staff member in this branch.");
        }
        return actor;
    }

    private List<JournalEntryResponse> buildEntryResponses(List<JournalEntry> entries) {
        if (entries.isEmpty()) return List.of();
        List<UUID> ids = entries.stream().map(JournalEntry::getId).toList();
        List<JournalLine> lines = em.createQuery("select l from JournalLine l where l.journalEntryId in :ids order by l.journalEntryId, l.lineNumber", JournalLine.class)
                .setParameter("ids", ids).getResultList();
        List<UUID> accountIds = lines.stream().map(JournalLine::getAccountId).distinct().toList();
        Map<UUID, ChartOfAccount> accounts = new HashMap<>();
        if (!accountIds.isEmpty()) em.createQuery("select a from ChartOfAccount a where a.id in :ids", ChartOfAccount.class)
                .setParameter("ids", accountIds).getResultList().forEach(a -> accounts.put(a.getId(), a));
        Map<UUID, List<JournalLine>> byEntry = new HashMap<>();
        lines.forEach(line -> byEntry.computeIfAbsent(line.getJournalEntryId(), ignored -> new ArrayList<>()).add(line));
        List<JournalEntryResponse> result = new ArrayList<>();
        for (JournalEntry entry : entries) {
            JournalEntryResponse dto = new JournalEntryResponse();
            dto.id = entry.getId(); dto.branchId = entry.getBranchId(); dto.entryNumber = entry.getEntryNumber();
            dto.entryDate = entry.getEntryDate(); dto.description = entry.getDescription(); dto.sourceModule = entry.getSourceModule();
            dto.sourceReferenceId = entry.getSourceReferenceId(); dto.status = entry.getEntryStatus().name();
            dto.reversalOfEntryId = entry.getReversalOfEntryId(); dto.createdBy = entry.getCreatedBy();
            dto.postedBy = entry.getPostedBy(); dto.createdAt = entry.getCreatedAt(); dto.postedAt = entry.getPostedAt();
            dto.lines = new ArrayList<>(); dto.totalDebit = ZERO; dto.totalCredit = ZERO;
            for (JournalLine line : byEntry.getOrDefault(entry.getId(), List.of())) {
                ChartOfAccount account = accounts.get(line.getAccountId());
                JournalEntryResponse.Line item = new JournalEntryResponse.Line();
                item.lineNumber = line.getLineNumber(); item.accountCode = account == null ? "?" : account.getAccountCode();
                item.accountName = account == null ? "Unknown account" : account.getAccountName();
                item.debitAmount = money(line.getDebitAmount()); item.creditAmount = money(line.getCreditAmount()); item.memo = line.getLineMemo();
                dto.lines.add(item); dto.totalDebit = dto.totalDebit.add(item.debitAmount); dto.totalCredit = dto.totalCredit.add(item.creditAmount);
            }
            result.add(dto);
        }
        return result;
    }

    private AccountResponse accountResponse(ChartOfAccount account) {
        AccountResponse dto = new AccountResponse(); dto.id = account.getId(); dto.branchId = account.getBranchId();
        dto.accountCode = account.getAccountCode(); dto.accountName = account.getAccountName();
        dto.accountType = account.getAccountType().name(); dto.normalBalance = account.getNormalBalance().name();
        dto.parentAccountId = account.getParentAccountId(); dto.controlAccount = account.isControlAccount();
        dto.allowsPosting = account.isAllowsPosting(); dto.active = account.isActive(); return dto;
    }

    private String journalNumber(UUID branchId, LocalDate date) {
        return "JE-" + date.toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
    private ZoneId branchZone(UUID branchId) {
        HospitalBranch branch = em.find(HospitalBranch.class, branchId);
        if (branch == null || !branch.isActive()) throw BusinessException.notFound("Active branch");
        try { return ZoneId.of(branch.getTimezone()); }
        catch (Exception exception) { return ZoneId.of("UTC"); }
    }
    private BigDecimal normalizeAmount(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) throw BusinessException.badRequest("JOURNAL_AMOUNT_INVALID", field + " must be zero or a positive amount.");
        try { return amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { throw BusinessException.badRequest("JOURNAL_AMOUNT_PRECISION_INVALID", "Journal amounts support at most two decimal places."); }
    }
    private BigDecimal money(Object value) { return value == null ? ZERO : ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP); }
    private String safeMemo(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record PreparedLine(ChartOfAccount account, BigDecimal debit, BigDecimal credit, String memo) { }
}
