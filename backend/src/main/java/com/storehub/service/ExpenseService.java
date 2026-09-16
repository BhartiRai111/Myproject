package com.storehub.service;

import com.storehub.dto.ExpenseCreateRequest;
import com.storehub.dto.ExpenseResponse;
import com.storehub.dto.ExpenseUpdateRequest;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Account;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.AuditAction;
import com.storehub.entity.Expense;
import com.storehub.entity.ExpenseCategory;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.VoucherDocType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ExpenseNotFoundException;
import com.storehub.repository.ExpenseRepository;
import com.storehub.repository.PaymentAllocationRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Operational Expense lifecycle (Phase 6 + Step 4 "Expenses Complete"). DRAFT
 * has no accounting effect. POSTED debits the Expense account — the linked
 * account of the expense's {@link ExpenseCategory} when set, else the generic
 * {@code SystemAccountCode.EXPENSES} — (and Input GST accounts when
 * {@code itcEligible}), then credits either Cash/Bank (no party) or Supplier
 * Payable, party-tagged (a credit/party expense), via the same
 * {@link AccountingService#postJournalByAccountId} every other voucher's
 * posting engine uses — never a manual P&L edit. CANCELLED reverses the
 * journal (and the payable sub-ledger, when applicable) and is idempotent.
 * A credit expense's later payment reuses the existing Payment module
 * (see {@link PaymentService}) — never a separate expense-payment mechanism.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final VoucherNumberService voucherNumberService;
    private final FinancialYearService financialYearService;
    private final AccountingService accountingService;
    private final AccountService accountService;
    private final ExpenseCategoryService expenseCategoryService;
    private final SupplierService supplierService;
    private final LedgerService ledgerService;
    private final GstTransactionSyncService gstTransactionSyncService;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final AuditService auditService;
    private final GstCalculationService gstCalculationService;

    @Transactional(readOnly = true)
    public PagedResponse<ExpenseResponse> search(String search, ExpenseStatus status, String category, Long categoryId,
                                                  Long supplierId, PaymentMode paymentMode, Boolean gstApplicable, Boolean itcEligible,
                                                  LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ExpenseResponse> result = expenseRepository.search(search, status, category, categoryId, supplierId,
                        paymentMode, gstApplicable, itcEligible, fromDate, toDate, pageable)
                .map(ExpenseResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getById(Long id) {
        return ExpenseResponse.fromEntity(findOrThrow(id));
    }

    /** Expense Summary report: category-wise, payment-method-wise, party-wise, and GST/ITC-wise totals — all server-aggregated. */
    @Transactional(readOnly = true)
    public com.storehub.dto.ExpenseSummaryReportResponse summary(LocalDate fromDate, LocalDate toDate) {
        List<com.storehub.dto.ExpenseSummaryGroupRow> byCategory = expenseRepository.sumByCategory(fromDate, toDate).stream()
                .map(row -> com.storehub.dto.ExpenseSummaryGroupRow.builder()
                        .key(String.valueOf(row[0])).label(String.valueOf(row[0]))
                        .totalAmount((BigDecimal) row[1]).count((Long) row[2]).build())
                .toList();

        List<com.storehub.dto.ExpenseSummaryGroupRow> byPaymentMode = expenseRepository.sumByPaymentMode(fromDate, toDate).stream()
                .map(row -> com.storehub.dto.ExpenseSummaryGroupRow.builder()
                        .key(String.valueOf(row[0])).label(String.valueOf(row[0]))
                        .totalAmount((BigDecimal) row[1]).count((Long) row[2]).build())
                .toList();

        List<com.storehub.dto.ExpenseSummaryGroupRow> byParty = expenseRepository.sumByParty(fromDate, toDate).stream()
                .map(row -> com.storehub.dto.ExpenseSummaryGroupRow.builder()
                        .key(String.valueOf(row[0])).label(String.valueOf(row[1]))
                        .totalAmount((BigDecimal) row[2]).count((Long) row[3]).build())
                .toList();

        BigDecimal totalAmount = byCategory.stream().map(com.storehub.dto.ExpenseSummaryGroupRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCount = byCategory.stream().mapToLong(com.storehub.dto.ExpenseSummaryGroupRow::getCount).sum();

        List<Object[]> gstRows = expenseRepository.gstSummary(fromDate, toDate);
        Object[] gst = gstRows.isEmpty() ? new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L} : gstRows.get(0);

        return com.storehub.dto.ExpenseSummaryReportResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalAmount(totalAmount)
                .totalCount(totalCount)
                .byCategory(byCategory)
                .byPaymentMode(byPaymentMode)
                .byParty(byParty)
                .gstApplicableAmount((BigDecimal) gst[0])
                .nonGstAmount((BigDecimal) gst[1])
                .itcEligibleTax((BigDecimal) gst[2])
                .itcIneligibleTax((BigDecimal) gst[3])
                .build();
    }

    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        Expense expense = new Expense();
        expense.setStatus(ExpenseStatus.DRAFT);
        expense.setCreatedBy(SecurityUtil.currentUsername());
        applyRequest(expense, request.getExpenseDate(), request.getCategoryId(), request.getCategory(),
                request.getSupplierId(), request.getVendorName(), request.getPaymentMode(), request.getTaxMode(),
                request.getGstPercent(), request.isItcEligible(), request.getGrossAmount(), request.getDiscountAmount(),
                request.getTaxableAmount(), request.getDescription(), request.getReferenceNumber(), request.getRemarks());

        Expense saved = expenseRepository.save(expense);
        saved.setExpenseNumber(voucherNumberService.next(VoucherDocType.EXPENSE, request.getExpenseDate()));
        saved = expenseRepository.save(saved);

        auditService.log(AuditAction.CREATE, "EXPENSE", "Expense", saved.getId(), saved.getExpenseNumber(),
                null, null, "Expense " + saved.getExpenseNumber() + " (" + saved.getCategory() + ") created");

        if (request.isPost()) {
            return post(saved.getId());
        }
        return ExpenseResponse.fromEntity(saved);
    }

    /** Edits a DRAFT expense in place; a POSTED/CANCELLED expense can never be edited (post/cancel/reverse only). */
    @Transactional
    public ExpenseResponse update(Long id, ExpenseUpdateRequest request) {
        Expense expense = findOrThrow(id);
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT expense can be edited (current status: " + expense.getStatus() + ")");
        }

        applyRequest(expense, request.getExpenseDate(), request.getCategoryId(), request.getCategory(),
                request.getSupplierId(), request.getVendorName(), request.getPaymentMode(), request.getTaxMode(),
                request.getGstPercent(), request.isItcEligible(), request.getGrossAmount(), request.getDiscountAmount(),
                request.getTaxableAmount(), request.getDescription(), request.getReferenceNumber(), request.getRemarks());

        Expense saved = expenseRepository.save(expense);
        auditService.log(AuditAction.UPDATE, "EXPENSE", "Expense", saved.getId(), saved.getExpenseNumber(),
                null, null, "Expense " + saved.getExpenseNumber() + " edited while in DRAFT");

        if (request.isPost()) {
            return post(saved.getId());
        }
        return ExpenseResponse.fromEntity(saved);
    }

    private void applyRequest(Expense expense, LocalDate expenseDate, Long categoryId, String categoryText,
                               Long supplierId, String vendorNameText, PaymentMode paymentMode, com.storehub.entity.TaxMode taxMode,
                               BigDecimal gstPercentRaw, boolean itcEligible, BigDecimal grossAmount, BigDecimal discountAmountRaw,
                               BigDecimal taxableAmountRaw, String description, String referenceNumber, String remarks) {
        ExpenseCategory expenseCategory = categoryId != null ? expenseCategoryService.findActiveOrThrow(categoryId) : null;
        String categoryName = expenseCategory != null ? expenseCategory.getName() : categoryText;
        if (categoryName == null || categoryName.isBlank()) {
            throw new BadRequestException("Category is required");
        }

        Supplier supplier = null;
        String vendorName = vendorNameText;
        if (supplierId != null) {
            supplier = supplierService.findSupplierOrThrow(supplierId);
            if (supplier.getStatus() == SupplierStatus.INACTIVE) {
                throw new BadRequestException("Supplier '" + supplier.getName() + "' is inactive and cannot be used for new expenses");
            }
            vendorName = supplier.getName();
        }

        BigDecimal discountAmount = discountAmountRaw != null ? discountAmountRaw : BigDecimal.ZERO;
        BigDecimal taxable;
        if (grossAmount != null) {
            taxable = grossAmount.subtract(discountAmount);
        } else {
            taxable = taxableAmountRaw;
        }
        if (taxable == null || taxable.signum() <= 0) {
            throw new BadRequestException("Taxable amount must be greater than 0");
        }

        BigDecimal gstPercent = gstPercentRaw != null ? gstPercentRaw : BigDecimal.ZERO;
        GstCalculationService.LineTaxResult tax = gstCalculationService.calculateLine(taxable, gstPercent, taxMode);
        if (tax.getGstAmount().signum() > 0 && taxMode == null) {
            throw new BadRequestException("Tax mode (Intra-State or Inter-State) is required when GST percent is set");
        }

        expense.setExpenseDate(expenseDate);
        expense.setExpenseCategory(expenseCategory);
        expense.setCategory(categoryName);
        expense.setSupplier(supplier);
        expense.setVendorName(vendorName);
        expense.setFinancialYearId(financialYearService.resolveForDate(expenseDate).getId());
        expense.setPaymentMode(paymentMode);
        expense.setTaxMode(tax.getGstAmount().signum() > 0 ? taxMode : null);
        expense.setGstPercent(gstPercent);
        expense.setItcEligible(itcEligible);
        expense.setDiscountAmount(discountAmount);
        expense.setTaxableAmount(taxable);
        expense.setCgstAmount(tax.getCgstAmount());
        expense.setSgstAmount(tax.getSgstAmount());
        expense.setIgstAmount(tax.getIgstAmount());
        expense.setTotalAmount(taxable.add(tax.getGstAmount()));
        expense.setDescription(description);
        expense.setReferenceNumber(referenceNumber);
        expense.setRemarks(remarks);
    }

    @Transactional
    public ExpenseResponse post(Long id) {
        Expense expense = findOrThrow(id);
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT expense can be posted (current status: " + expense.getStatus() + ")");
        }

        BigDecimal totalTax = expense.getCgstAmount().add(expense.getSgstAmount()).add(expense.getIgstAmount());
        BigDecimal expenseLineAmount = expense.isItcEligible()
                ? expense.getTaxableAmount()
                : expense.getTaxableAmount().add(totalTax);

        Long expenseAccountId = resolveExpenseAccountId(expense);

        List<ManualJournalLine> lines = new ArrayList<>();
        lines.add(new ManualJournalLine(expenseAccountId, expenseLineAmount, BigDecimal.ZERO, null, null, null));
        if (expense.isItcEligible()) {
            addIfPositive(lines, SystemAccountCode.INPUT_CGST, expense.getCgstAmount());
            addIfPositive(lines, SystemAccountCode.INPUT_SGST, expense.getSgstAmount());
            addIfPositive(lines, SystemAccountCode.INPUT_IGST, expense.getIgstAmount());
        }

        boolean creditExpense = expense.getSupplier() != null;
        if (creditExpense) {
            lines.add(new ManualJournalLine(accountService.getSystemAccount(SystemAccountCode.SUPPLIER_PAYABLE).getId(),
                    BigDecimal.ZERO, expense.getTotalAmount(), null, AccountingPartyType.SUPPLIER, expense.getSupplier().getId()));
        } else {
            lines.add(new ManualJournalLine(accountService.getSystemAccount(accountingService.resolveCashOrBank(expense.getPaymentMode())).getId(),
                    BigDecimal.ZERO, expense.getTotalAmount(), null, null, null));
        }

        accountingService.postJournalByAccountId(VoucherType.EXPENSE, expense.getId(), expense.getExpenseNumber(),
                expense.getExpenseDate(), "Expense " + expense.getExpenseNumber() + ": " + expense.getCategory(), lines);

        if (creditExpense) {
            ledgerService.recordExpenseCredit(expense);
            expense.setPaidAmount(BigDecimal.ZERO);
            expense.setPayableAmount(expense.getTotalAmount());
            expense.setPaymentStatus(PaymentStatus.UNPAID);
        } else {
            expense.setPaidAmount(expense.getTotalAmount());
            expense.setPayableAmount(BigDecimal.ZERO);
            expense.setPaymentStatus(PaymentStatus.PAID);
        }

        expense.setStatus(ExpenseStatus.POSTED);
        expense.setPostedBy(SecurityUtil.currentUsername());
        expense.setPostedAt(LocalDateTime.now());
        Expense posted = expenseRepository.save(expense);

        gstTransactionSyncService.syncExpense(posted);

        auditService.log(AuditAction.POST, "EXPENSE", "Expense", posted.getId(), posted.getExpenseNumber(),
                null, null, "Expense " + posted.getExpenseNumber() + " posted: accounting journal applied");
        return ExpenseResponse.fromEntity(posted);
    }

    /** Idempotent: cancelling an already-CANCELLED expense is a no-op that returns its current state. */
    @Transactional
    public ExpenseResponse cancel(Long id) {
        Expense expense = findOrThrow(id);
        if (expense.getStatus() == ExpenseStatus.CANCELLED) {
            return ExpenseResponse.fromEntity(expense);
        }
        if (!paymentAllocationRepository.findByExpenseId(expense.getId()).isEmpty()) {
            throw new BadRequestException("This expense has one or more payments recorded against it. "
                    + "Delete those payments first before cancelling the expense.");
        }

        String reason = "Expense cancelled: " + expense.getExpenseNumber();
        if (expense.getStatus() == ExpenseStatus.POSTED) {
            accountingService.reverseJournal(VoucherType.EXPENSE, expense.getId(), reason);
            if (expense.getSupplier() != null) {
                ledgerService.reverseExpenseCredit(expense, reason);
            }
            gstTransactionSyncService.reverseExpense(expense);
        }

        expense.setPaidAmount(BigDecimal.ZERO);
        expense.setPayableAmount(BigDecimal.ZERO);
        expense.setStatus(ExpenseStatus.CANCELLED);
        expense.setCancelledBy(SecurityUtil.currentUsername());
        expense.setCancelledAt(LocalDateTime.now());
        Expense cancelled = expenseRepository.save(expense);

        auditService.log(AuditAction.CANCEL, "EXPENSE", "Expense", cancelled.getId(), cancelled.getExpenseNumber(),
                null, null, reason);
        return ExpenseResponse.fromEntity(cancelled);
    }

    /** The category's linked account when set and active, else the generic SystemAccountCode.EXPENSES account. */
    private Long resolveExpenseAccountId(Expense expense) {
        ExpenseCategory category = expense.getExpenseCategory();
        if (category != null && category.getLinkedAccount() != null) {
            Account linked = category.getLinkedAccount();
            if (linked.isActive()) {
                return linked.getId();
            }
        }
        return accountService.getSystemAccount(SystemAccountCode.EXPENSES).getId();
    }

    private void addIfPositive(List<ManualJournalLine> lines, SystemAccountCode account, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            lines.add(new ManualJournalLine(accountService.getSystemAccount(account).getId(), amount, BigDecimal.ZERO, null, null, null));
        }
    }

    private Expense findOrThrow(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> new ExpenseNotFoundException(id));
    }
}
