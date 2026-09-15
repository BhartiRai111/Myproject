package com.storehub.service;

import com.storehub.dto.ExpenseCreateRequest;
import com.storehub.dto.ExpenseResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.Expense;
import com.storehub.entity.ExpenseStatus;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.TaxMode;
import com.storehub.entity.VoucherDocType;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ExpenseNotFoundException;
import com.storehub.repository.ExpenseRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Operational Expense lifecycle (Phase 6 spec sections 10-11). DRAFT has no
 * accounting effect. POSTED debits the Expense account (and Input GST
 * accounts when {@code itcEligible}) and credits Cash/Bank via the same
 * {@link AccountingService#postJournal}/{@link AccountingService#resolveCashOrBank}
 * every other cash/bank-settled voucher uses — never a manual P&L edit or a
 * parallel GST calculation. CANCELLED reverses the journal and is idempotent.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final VoucherNumberService voucherNumberService;
    private final FinancialYearService financialYearService;
    private final AccountingService accountingService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PagedResponse<ExpenseResponse> search(String search, ExpenseStatus status, String category,
                                                  LocalDate fromDate, LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ExpenseResponse> result = expenseRepository.search(search, status, category, fromDate, toDate, pageable)
                .map(ExpenseResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getById(Long id) {
        return ExpenseResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        BigDecimal taxable = request.getTaxableAmount();
        BigDecimal gstPercent = request.getGstPercent() != null ? request.getGstPercent() : BigDecimal.ZERO;
        BigDecimal gstAmount = gstPercent.signum() > 0
                ? taxable.multiply(gstPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO, igst = BigDecimal.ZERO;
        if (gstAmount.signum() > 0) {
            if (request.getTaxMode() == null) {
                throw new BadRequestException("Tax mode (Intra-State or Inter-State) is required when GST percent is set");
            }
            if (request.getTaxMode() == TaxMode.INTER_STATE) {
                igst = gstAmount;
            } else {
                cgst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sgst = gstAmount.subtract(cgst);
            }
        }

        Expense expense = Expense.builder()
                .expenseDate(request.getExpenseDate())
                .category(request.getCategory())
                .vendorName(request.getVendorName())
                .financialYearId(financialYearService.resolveForDate(request.getExpenseDate()).getId())
                .paymentMode(request.getPaymentMode())
                .taxMode(gstAmount.signum() > 0 ? request.getTaxMode() : null)
                .gstPercent(gstPercent)
                .itcEligible(request.isItcEligible())
                .taxableAmount(taxable)
                .cgstAmount(cgst)
                .sgstAmount(sgst)
                .igstAmount(igst)
                .totalAmount(taxable.add(cgst).add(sgst).add(igst))
                .description(request.getDescription())
                .createdBy(SecurityUtil.currentUsername())
                .status(ExpenseStatus.DRAFT)
                .build();

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

    @Transactional
    public ExpenseResponse post(Long id) {
        Expense expense = findOrThrow(id);
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT expense can be posted (current status: " + expense.getStatus() + ")");
        }

        List<JournalLine> lines = new ArrayList<>();
        BigDecimal totalTax = expense.getCgstAmount().add(expense.getSgstAmount()).add(expense.getIgstAmount());
        BigDecimal expenseLineAmount = expense.isItcEligible()
                ? expense.getTaxableAmount()
                : expense.getTaxableAmount().add(totalTax);

        lines.add(JournalLine.debit(SystemAccountCode.EXPENSES, expenseLineAmount));
        if (expense.isItcEligible()) {
            addIfPositive(lines, SystemAccountCode.INPUT_CGST, expense.getCgstAmount());
            addIfPositive(lines, SystemAccountCode.INPUT_SGST, expense.getSgstAmount());
            addIfPositive(lines, SystemAccountCode.INPUT_IGST, expense.getIgstAmount());
        }
        lines.add(JournalLine.credit(accountingService.resolveCashOrBank(expense.getPaymentMode()), expense.getTotalAmount()));

        accountingService.postJournal(VoucherType.EXPENSE, expense.getId(), expense.getExpenseNumber(),
                expense.getExpenseDate(), "Expense " + expense.getExpenseNumber() + ": " + expense.getCategory(), lines);

        expense.setStatus(ExpenseStatus.POSTED);
        expense.setPostedBy(SecurityUtil.currentUsername());
        expense.setPostedAt(LocalDateTime.now());
        Expense posted = expenseRepository.save(expense);

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

        String reason = "Expense cancelled: " + expense.getExpenseNumber();
        if (expense.getStatus() == ExpenseStatus.POSTED) {
            accountingService.reverseJournal(VoucherType.EXPENSE, expense.getId(), reason);
        }

        expense.setStatus(ExpenseStatus.CANCELLED);
        expense.setCancelledBy(SecurityUtil.currentUsername());
        expense.setCancelledAt(LocalDateTime.now());
        Expense cancelled = expenseRepository.save(expense);

        auditService.log(AuditAction.CANCEL, "EXPENSE", "Expense", cancelled.getId(), cancelled.getExpenseNumber(),
                null, null, reason);
        return ExpenseResponse.fromEntity(cancelled);
    }

    private void addIfPositive(List<JournalLine> lines, SystemAccountCode account, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            lines.add(JournalLine.debit(account, amount));
        }
    }

    private Expense findOrThrow(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> new ExpenseNotFoundException(id));
    }
}
