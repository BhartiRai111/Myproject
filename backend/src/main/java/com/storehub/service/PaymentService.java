package com.storehub.service;

import com.storehub.dto.OutstandingPurchaseBillResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.PaymentAllocationRequest;
import com.storehub.dto.PaymentRequest;
import com.storehub.dto.PaymentResponse;
import com.storehub.dto.SupplierOutstandingResponse;
import com.storehub.entity.Expense;
import com.storehub.entity.PaymentAllocation;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Payment;
import com.storehub.entity.Purchase;
import com.storehub.entity.Store;
import com.storehub.entity.Supplier;
import com.storehub.entity.User;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ExpenseNotFoundException;
import com.storehub.exception.PaymentNotFoundException;
import com.storehub.exception.PurchaseNotFoundException;
import com.storehub.repository.ExpenseRepository;
import com.storehub.repository.PaymentRepository;
import com.storehub.repository.PurchaseRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every Supplier payment — whether settling a Purchase Bill or a credit
 * (party) Expense — goes through this one module. A credit Expense's later
 * payment reuses this exact allocation mechanism (see
 * {@link PaymentAllocation#getExpense()}) rather than a parallel
 * expense-payment path.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;
    private final SupplierService supplierService;
    private final LedgerService ledgerService;
    private final AccountingService accountingService;
    private final VoucherNumberService voucherNumberService;
    private final AuditService auditService;
    private final StoreAccessService storeAccessService;
    private final StoreService storeService;

    public PagedResponse<PaymentResponse> search(String search, Long supplierId, LocalDate fromDate, LocalDate toDate,
                                                  Long storeId, int page, int size) {
        Long resolvedStoreId = storeAccessService.resolveViewableStoreId(SecurityUtil.currentUserOrNull(), storeId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PaymentResponse> result = paymentRepository.search(search, supplierId, fromDate, toDate, resolvedStoreId, pageable)
                .map(PaymentResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    /** A payment belongs to exactly one store — never returned to a caller without access to it (Multi-Store spec section 14). */
    public PaymentResponse getById(Long id) {
        Payment payment = findOrThrow(id);
        storeAccessService.assertStoreAccess(SecurityUtil.currentUserOrNull(), payment.getStore() != null ? payment.getStore().getId() : null);
        return PaymentResponse.fromEntity(payment);
    }

    public SupplierOutstandingResponse getOutstandingForSupplier(Long supplierId) {
        supplierService.findSupplierOrThrow(supplierId);
        List<Purchase> outstanding = purchaseRepository.findOutstandingBySupplier(supplierId);
        BigDecimal total = outstanding.stream().map(Purchase::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Expense> outstandingExpenseList = expenseRepository.findOutstandingBySupplier(supplierId);
        BigDecimal outstandingExpenses = outstandingExpenseList.stream()
                .map(Expense::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return SupplierOutstandingResponse.builder()
                .supplierId(supplierId)
                .totalOutstanding(total.add(outstandingExpenses))
                .bills(outstanding.stream().map(OutstandingPurchaseBillResponse::fromEntity).toList())
                .expenses(outstandingExpenseList.stream().map(com.storehub.dto.OutstandingExpenseResponse::fromEntity).toList())
                .build();
    }

    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());

        User currentUser = SecurityUtil.currentUserOrNull();
        Long resolvedStoreId = storeAccessService.resolveEffectiveStoreId(currentUser, request.getStoreId());
        Store store = storeService.findOrThrow(resolvedStoreId);

        Payment payment = Payment.builder()
                .supplier(supplier)
                .store(store)
                .paymentDate(request.getPaymentDate())
                .amount(request.getAmount())
                .paymentMode(request.getPaymentMode())
                .remarks(request.getRemarks())
                .systemGenerated(false)
                .build();

        Payment saved = paymentRepository.save(payment);
        saved.setPaymentNumber(voucherNumberService.next(com.storehub.entity.VoucherDocType.PAYMENT, saved.getPaymentDate()));

        allocate(saved, request.getAllocations(), supplier.getId());

        saved = paymentRepository.save(saved);
        ledgerService.recordPaymentDebit(saved);
        ledgerService.recordCashEntryOut(saved);
        accountingService.postPaymentJournal(saved);

        auditService.log(com.storehub.entity.AuditAction.PAYMENT, "PURCHASE", "Payment", saved.getId(), saved.getPaymentNumber(),
                null, null, "Payment " + saved.getPaymentNumber() + " of " + saved.getAmount() + " recorded for supplier " + supplier.getId(),
                saved.getStore() != null ? saved.getStore().getId() : null);

        return PaymentResponse.fromEntity(saved);
    }

    /** Called by PurchaseService when a Purchase Bill records an immediate payment. */
    @Transactional
    public Payment createSystemPaymentForPurchase(Purchase purchase) {
        Payment payment = Payment.builder()
                .supplier(purchase.getSupplier())
                .store(purchase.getStore())
                .paymentDate(purchase.getPurchaseDate())
                .amount(purchase.getPaidAmount())
                .paymentMode(purchase.getPaymentMode())
                .remarks("Auto-generated on purchase " + purchase.getPurchaseNumber())
                .systemGenerated(true)
                .build();

        Payment saved = paymentRepository.save(payment);
        saved.setPaymentNumber(voucherNumberService.next(com.storehub.entity.VoucherDocType.PAYMENT, saved.getPaymentDate()));
        saved.addAllocation(PaymentAllocation.builder().purchase(purchase).amountApplied(purchase.getPaidAmount()).build());
        saved = paymentRepository.save(saved);

        ledgerService.recordPaymentDebit(saved);
        ledgerService.recordCashEntryOut(saved);
        accountingService.postPaymentJournal(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Payment payment = findOrThrow(id);
        if (payment.isSystemGenerated()) {
            throw new BadRequestException("This payment was auto-generated with its purchase bill. "
                    + "Delete the purchase bill instead to reverse it.");
        }
        String paymentNumber = payment.getPaymentNumber();
        Long paymentId = payment.getId();
        Long storeId = payment.getStore() != null ? payment.getStore().getId() : null;
        reverseAndRemove(payment);
        auditService.log(com.storehub.entity.AuditAction.CANCEL, "PURCHASE", "Payment", paymentId, paymentNumber,
                null, null, "Payment " + paymentNumber + " deleted: allocations and ledger/accounting effects reversed", storeId);
    }

    /** Used only by PurchaseService when reversing a purchase that has its own auto-generated payment. */
    @Transactional
    void deleteSystemPayment(Long id) {
        Payment payment = findOrThrow(id);
        reverseAndRemove(payment);
    }

    private void reverseAndRemove(Payment payment) {
        for (PaymentAllocation allocation : payment.getAllocations()) {
            if (allocation.getPurchase() != null) {
                Purchase purchase = allocation.getPurchase();
                purchase.setPaidAmount(purchase.getPaidAmount().subtract(allocation.getAmountApplied()));
                purchase.setPayableAmount(purchase.getPayableAmount().add(allocation.getAmountApplied()));
                purchase.setPaymentStatus(derivePaymentStatus(purchase.getPaidAmount(), purchase.getTotalAmount()));
                purchaseRepository.save(purchase);
            } else if (allocation.getExpense() != null) {
                Expense expense = allocation.getExpense();
                expense.setPaidAmount(expense.getPaidAmount().subtract(allocation.getAmountApplied()));
                expense.setPayableAmount(expense.getPayableAmount().add(allocation.getAmountApplied()));
                expense.setPaymentStatus(derivePaymentStatus(expense.getPaidAmount(), expense.getTotalAmount()));
                expenseRepository.save(expense);
            }
        }
        String reason = (payment.isSystemGenerated() ? "Purchase reversed: " : "Payment deleted: ")
                + payment.getPaymentNumber();
        ledgerService.reversePaymentDebit(payment, reason);
        ledgerService.reverseCashEntryOut(payment, reason);
        accountingService.reversePaymentJournal(payment, reason);
        paymentRepository.delete(payment);
    }

    private void allocate(Payment payment, List<PaymentAllocationRequest> explicit, Long supplierId) {
        BigDecimal remaining = payment.getAmount();

        if (explicit != null && !explicit.isEmpty()) {
            BigDecimal totalExplicit = BigDecimal.ZERO;
            for (PaymentAllocationRequest allocationRequest : explicit) {
                if (allocationRequest.getPurchaseId() == null && allocationRequest.getExpenseId() == null) {
                    throw new BadRequestException("Each allocation must reference either a purchase bill or an expense");
                }
                if (allocationRequest.getPurchaseId() != null && allocationRequest.getExpenseId() != null) {
                    throw new BadRequestException("An allocation cannot reference both a purchase bill and an expense");
                }

                if (allocationRequest.getPurchaseId() != null) {
                    Purchase purchase = purchaseRepository.findById(allocationRequest.getPurchaseId())
                            .orElseThrow(() -> new PurchaseNotFoundException(allocationRequest.getPurchaseId()));
                    if (purchase.getSupplier() == null || !purchase.getSupplier().getId().equals(supplierId)) {
                        throw new BadRequestException("Purchase " + purchase.getPurchaseNumber() + " does not belong to this supplier");
                    }
                    if (allocationRequest.getAmountApplied().compareTo(purchase.getPayableAmount()) > 0) {
                        throw new BadRequestException("Allocation of " + allocationRequest.getAmountApplied()
                                + " for " + purchase.getPurchaseNumber() + " exceeds its payable amount of " + purchase.getPayableAmount());
                    }
                    applyAllocation(payment, purchase, allocationRequest.getAmountApplied());
                } else {
                    Expense expense = expenseRepository.findById(allocationRequest.getExpenseId())
                            .orElseThrow(() -> new ExpenseNotFoundException(allocationRequest.getExpenseId()));
                    if (expense.getSupplier() == null || !expense.getSupplier().getId().equals(supplierId)) {
                        throw new BadRequestException("Expense " + expense.getExpenseNumber() + " does not belong to this supplier");
                    }
                    if (allocationRequest.getAmountApplied().compareTo(expense.getPayableAmount()) > 0) {
                        throw new BadRequestException("Allocation of " + allocationRequest.getAmountApplied()
                                + " for " + expense.getExpenseNumber() + " exceeds its payable amount of " + expense.getPayableAmount());
                    }
                    applyExpenseAllocation(payment, expense, allocationRequest.getAmountApplied());
                }
                totalExplicit = totalExplicit.add(allocationRequest.getAmountApplied());
            }
            if (totalExplicit.compareTo(payment.getAmount()) > 0) {
                throw new BadRequestException("Total allocations (" + totalExplicit
                        + ") cannot exceed the payment amount (" + payment.getAmount() + ")");
            }
        } else {
            // FIFO across both outstanding purchase bills and outstanding credit expenses for this supplier, oldest first.
            List<Outstanding> combined = new ArrayList<>();
            for (Purchase p : purchaseRepository.findOutstandingBySupplier(supplierId)) {
                combined.add(new Outstanding(p.getPurchaseDate(), p, null));
            }
            for (Expense e : expenseRepository.findOutstandingBySupplier(supplierId)) {
                combined.add(new Outstanding(e.getExpenseDate(), null, e));
            }
            combined.sort(Comparator.comparing(Outstanding::date));

            for (Outstanding item : combined) {
                if (remaining.signum() <= 0) {
                    break;
                }
                if (item.purchase() != null) {
                    BigDecimal toApply = remaining.min(item.purchase().getPayableAmount());
                    applyAllocation(payment, item.purchase(), toApply);
                    remaining = remaining.subtract(toApply);
                } else {
                    BigDecimal toApply = remaining.min(item.expense().getPayableAmount());
                    applyExpenseAllocation(payment, item.expense(), toApply);
                    remaining = remaining.subtract(toApply);
                }
            }
            // Any amount left over is recorded as an on-account credit against the supplier
            // (still reduces overall outstanding via the ledger DEBIT entry) rather than tied to one bill/expense.
        }
    }

    private record Outstanding(LocalDate date, Purchase purchase, Expense expense) {
    }

    private void applyAllocation(Payment payment, Purchase purchase, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        payment.addAllocation(PaymentAllocation.builder().purchase(purchase).amountApplied(amount).build());
        purchase.setPaidAmount(purchase.getPaidAmount().add(amount));
        purchase.setPayableAmount(purchase.getPayableAmount().subtract(amount));
        purchase.setPaymentStatus(derivePaymentStatus(purchase.getPaidAmount(), purchase.getTotalAmount()));
        purchaseRepository.save(purchase);
    }

    private void applyExpenseAllocation(Payment payment, Expense expense, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        payment.addAllocation(PaymentAllocation.builder().expense(expense).amountApplied(amount).build());
        expense.setPaidAmount(expense.getPaidAmount().add(amount));
        expense.setPayableAmount(expense.getPayableAmount().subtract(amount));
        expense.setPaymentStatus(derivePaymentStatus(expense.getPaidAmount(), expense.getTotalAmount()));
        expenseRepository.save(expense);
    }

    private PaymentStatus derivePaymentStatus(BigDecimal paid, BigDecimal total) {
        if (total.signum() <= 0 || paid.compareTo(total) >= 0) {
            return PaymentStatus.PAID;
        }
        if (paid.signum() <= 0) {
            return PaymentStatus.UNPAID;
        }
        return PaymentStatus.PARTIAL;
    }

    public Payment findOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
