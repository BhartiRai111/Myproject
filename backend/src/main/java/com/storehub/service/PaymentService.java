package com.storehub.service;

import com.storehub.dto.OutstandingPurchaseBillResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.PaymentAllocationRequest;
import com.storehub.dto.PaymentRequest;
import com.storehub.dto.PaymentResponse;
import com.storehub.dto.SupplierOutstandingResponse;
import com.storehub.entity.PaymentAllocation;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Payment;
import com.storehub.entity.Purchase;
import com.storehub.entity.Supplier;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.PaymentNotFoundException;
import com.storehub.exception.PurchaseNotFoundException;
import com.storehub.repository.PaymentRepository;
import com.storehub.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierService supplierService;
    private final LedgerService ledgerService;

    public PagedResponse<PaymentResponse> search(String search, Long supplierId, LocalDate fromDate, LocalDate toDate,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PaymentResponse> result = paymentRepository.search(search, supplierId, fromDate, toDate, pageable)
                .map(PaymentResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public PaymentResponse getById(Long id) {
        return PaymentResponse.fromEntity(findOrThrow(id));
    }

    public SupplierOutstandingResponse getOutstandingForSupplier(Long supplierId) {
        supplierService.findSupplierOrThrow(supplierId);
        List<Purchase> outstanding = purchaseRepository.findOutstandingBySupplier(supplierId);
        BigDecimal total = outstanding.stream().map(Purchase::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return SupplierOutstandingResponse.builder()
                .supplierId(supplierId)
                .totalOutstanding(total)
                .bills(outstanding.stream().map(OutstandingPurchaseBillResponse::fromEntity).toList())
                .build();
    }

    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        Supplier supplier = supplierService.findSupplierOrThrow(request.getSupplierId());

        Payment payment = Payment.builder()
                .supplier(supplier)
                .paymentDate(request.getPaymentDate())
                .amount(request.getAmount())
                .paymentMode(request.getPaymentMode())
                .remarks(request.getRemarks())
                .systemGenerated(false)
                .build();

        Payment saved = paymentRepository.save(payment);
        saved.setPaymentNumber(String.format("PAY-%06d", saved.getId()));

        allocate(saved, request.getAllocations(), supplier.getId());

        saved = paymentRepository.save(saved);
        ledgerService.recordPaymentDebit(saved);
        ledgerService.recordCashEntryOut(saved);

        return PaymentResponse.fromEntity(saved);
    }

    /** Called by PurchaseService when a Purchase Bill records an immediate payment. */
    @Transactional
    public Payment createSystemPaymentForPurchase(Purchase purchase) {
        Payment payment = Payment.builder()
                .supplier(purchase.getSupplier())
                .paymentDate(purchase.getPurchaseDate())
                .amount(purchase.getPaidAmount())
                .paymentMode(purchase.getPaymentMode())
                .remarks("Auto-generated on purchase " + purchase.getPurchaseNumber())
                .systemGenerated(true)
                .build();

        Payment saved = paymentRepository.save(payment);
        saved.setPaymentNumber(String.format("PAY-%06d", saved.getId()));
        saved.addAllocation(PaymentAllocation.builder().purchase(purchase).amountApplied(purchase.getPaidAmount()).build());
        saved = paymentRepository.save(saved);

        ledgerService.recordPaymentDebit(saved);
        ledgerService.recordCashEntryOut(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Payment payment = findOrThrow(id);
        if (payment.isSystemGenerated()) {
            throw new BadRequestException("This payment was auto-generated with its purchase bill. "
                    + "Delete the purchase bill instead to reverse it.");
        }
        reverseAndRemove(payment);
    }

    /** Used only by PurchaseService when reversing a purchase that has its own auto-generated payment. */
    @Transactional
    void deleteSystemPayment(Long id) {
        Payment payment = findOrThrow(id);
        reverseAndRemove(payment);
    }

    private void reverseAndRemove(Payment payment) {
        for (PaymentAllocation allocation : payment.getAllocations()) {
            Purchase purchase = allocation.getPurchase();
            purchase.setPaidAmount(purchase.getPaidAmount().subtract(allocation.getAmountApplied()));
            purchase.setPayableAmount(purchase.getPayableAmount().add(allocation.getAmountApplied()));
            purchase.setPaymentStatus(derivePaymentStatus(purchase.getPaidAmount(), purchase.getTotalAmount()));
            purchaseRepository.save(purchase);
        }
        String reason = (payment.isSystemGenerated() ? "Purchase reversed: " : "Payment deleted: ")
                + payment.getPaymentNumber();
        ledgerService.reversePaymentDebit(payment, reason);
        ledgerService.reverseCashEntryOut(payment, reason);
        paymentRepository.delete(payment);
    }

    private void allocate(Payment payment, List<PaymentAllocationRequest> explicit, Long supplierId) {
        BigDecimal remaining = payment.getAmount();

        if (explicit != null && !explicit.isEmpty()) {
            BigDecimal totalExplicit = BigDecimal.ZERO;
            for (PaymentAllocationRequest allocationRequest : explicit) {
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
                totalExplicit = totalExplicit.add(allocationRequest.getAmountApplied());
            }
            if (totalExplicit.compareTo(payment.getAmount()) > 0) {
                throw new BadRequestException("Total allocations (" + totalExplicit
                        + ") cannot exceed the payment amount (" + payment.getAmount() + ")");
            }
        } else {
            List<Purchase> outstanding = purchaseRepository.findOutstandingBySupplier(supplierId);
            for (Purchase purchase : outstanding) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal toApply = remaining.min(purchase.getPayableAmount());
                applyAllocation(payment, purchase, toApply);
                remaining = remaining.subtract(toApply);
            }
            // Any amount left over is recorded as an on-account credit against the supplier
            // (still reduces overall outstanding via the ledger DEBIT entry) rather than tied to one bill.
        }
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
