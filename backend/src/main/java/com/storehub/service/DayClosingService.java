package com.storehub.service;

import com.storehub.dto.CashBankBookResponse;
import com.storehub.dto.DayClosingCloseRequest;
import com.storehub.dto.DayClosingResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.CashTransactionType;
import com.storehub.entity.DayClosing;
import com.storehub.entity.PaymentMode;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.CashTransactionRepository;
import com.storehub.repository.DayClosingRepository;
import com.storehub.repository.ExpenseRepository;
import com.storehub.repository.PaymentRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.ReceiptRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Day End / Cash Closing (Phase 6 spec sections 16-17). {@code computeSummary}
 * is read-only and safe to call any number of times before closing (e.g. to
 * preview); {@code close} persists exactly one {@link DayClosing} per date.
 * Never blocks a backdated transaction after closing — that decision is left
 * to Financial Year open/closed status (Phase 5), the one real posting gate.
 */
@Service
@RequiredArgsConstructor
public class DayClosingService {

    private final DayClosingRepository dayClosingRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final CashBankBookService cashBankBookService;
    private final FinancialYearService financialYearService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public DayClosingResponse computeSummary(LocalDate date) {
        Map<PaymentMode, BigDecimal> byMode = new EnumMap<>(PaymentMode.class);
        for (Object[] row : saleRepository.sumByPaymentModeForDate(date)) {
            byMode.put((PaymentMode) row[0], (BigDecimal) row[1]);
        }

        CashBankBookResponse cashBook = cashBankBookService.cashBook(date, date);

        DayClosing existing = dayClosingRepository.findByClosingDate(date).orElse(null);

        DayClosingResponse computed = DayClosingResponse.builder()
                .closingDate(date)
                .closed(existing != null)
                .totalSales(saleRepository.getTotalSalesForDate(date))
                .totalPurchases(purchaseRepository.getTotalPurchasesForDate(date))
                .cashSales(byMode.getOrDefault(PaymentMode.CASH, BigDecimal.ZERO))
                .cardSales(byMode.getOrDefault(PaymentMode.CARD, BigDecimal.ZERO))
                .upiSales(byMode.getOrDefault(PaymentMode.UPI, BigDecimal.ZERO))
                .creditSales(saleRepository.sumCreditSalesForDate(date))
                .totalReceipts(receiptRepository.sumAmountForDate(date))
                .totalPayments(paymentRepository.sumAmountForDate(date))
                .totalExpenses(expenseRepository.getTotalExpensesForDate(date))
                .cashIn(cashTransactionRepository.sumForDateAndType(date, CashTransactionType.CASH_IN))
                .cashOut(cashTransactionRepository.sumForDateAndType(date, CashTransactionType.CASH_OUT))
                .expectedCash(cashBook.getClosingBalance())
                .build();

        if (existing == null) {
            return computed;
        }
        return DayClosingResponse.fromClosed(existing, computed);
    }

    @Transactional(readOnly = true)
    public List<DayClosingResponse> history() {
        return dayClosingRepository.findAllByOrderByClosingDateDesc().stream()
                .map(d -> computeSummary(d.getClosingDate()))
                .toList();
    }

    @Transactional
    public DayClosingResponse close(DayClosingCloseRequest request) {
        if (dayClosingRepository.existsByClosingDate(request.getClosingDate())) {
            throw new BadRequestException("Day " + request.getClosingDate() + " has already been closed");
        }

        DayClosingResponse computed = computeSummary(request.getClosingDate());
        BigDecimal difference = request.getActualCash().subtract(computed.getExpectedCash());
        if (difference.signum() != 0 && (request.getDifferenceReason() == null || request.getDifferenceReason().isBlank())) {
            throw new BadRequestException("Expected cash " + computed.getExpectedCash() + " does not match actual cash "
                    + request.getActualCash() + " (difference " + difference + "). A reason is required to close with a difference.");
        }

        DayClosing closing = DayClosing.builder()
                .closingDate(request.getClosingDate())
                .expectedCash(computed.getExpectedCash())
                .actualCash(request.getActualCash())
                .difference(difference)
                .differenceReason(request.getDifferenceReason())
                .financialYearId(financialYearService.resolveForDate(request.getClosingDate()).getId())
                .closedBy(SecurityUtil.currentUsername())
                .closedAt(LocalDateTime.now())
                .build();

        DayClosing saved = dayClosingRepository.save(closing);

        auditService.log(AuditAction.DAY_CLOSE, "CASH", "DayClosing", saved.getId(), saved.getClosingDate().toString(),
                null, null, "Day " + saved.getClosingDate() + " closed by " + saved.getClosedBy()
                        + (difference.signum() != 0 ? " with cash difference " + difference + ": " + saved.getDifferenceReason() : ""));

        return DayClosingResponse.fromClosed(saved, computed);
    }
}
