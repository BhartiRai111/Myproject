package com.storehub.service;

import com.storehub.dto.CustomerOutstandingResponse;
import com.storehub.dto.OutstandingBillResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.ReceiptAllocationRequest;
import com.storehub.dto.ReceiptRequest;
import com.storehub.dto.ReceiptResponse;
import com.storehub.entity.Customer;
import com.storehub.entity.PaymentStatus;
import com.storehub.entity.Receipt;
import com.storehub.entity.ReceiptAllocation;
import com.storehub.entity.Sale;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ReceiptNotFoundException;
import com.storehub.exception.SaleNotFoundException;
import com.storehub.repository.ReceiptRepository;
import com.storehub.repository.SaleRepository;
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
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final SaleRepository saleRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;

    public PagedResponse<ReceiptResponse> search(String search, Long customerId, LocalDate fromDate, LocalDate toDate,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReceiptResponse> result = receiptRepository.search(search, customerId, fromDate, toDate, pageable)
                .map(ReceiptResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public ReceiptResponse getById(Long id) {
        return ReceiptResponse.fromEntity(findOrThrow(id));
    }

    public CustomerOutstandingResponse getOutstandingForCustomer(Long customerId) {
        customerService.findCustomerOrThrow(customerId);
        List<Sale> outstanding = saleRepository.findOutstandingByCustomer(customerId);
        BigDecimal total = outstanding.stream().map(Sale::getDueAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return CustomerOutstandingResponse.builder()
                .customerId(customerId)
                .totalOutstanding(total)
                .bills(outstanding.stream().map(OutstandingBillResponse::fromEntity).toList())
                .build();
    }

    @Transactional
    public ReceiptResponse create(ReceiptRequest request) {
        Customer customer = customerService.findCustomerOrThrow(request.getCustomerId());

        Receipt receipt = Receipt.builder()
                .customer(customer)
                .receiptDate(request.getReceiptDate())
                .amount(request.getAmount())
                .paymentMode(request.getPaymentMode())
                .remarks(request.getRemarks())
                .systemGenerated(false)
                .build();

        Receipt saved = receiptRepository.save(receipt);
        saved.setReceiptNumber(String.format("RCPT-%06d", saved.getId()));

        allocate(saved, request.getAllocations(), customer.getId());

        saved = receiptRepository.save(saved);
        ledgerService.recordReceiptCredit(saved);
        ledgerService.recordCashEntry(saved);

        return ReceiptResponse.fromEntity(saved);
    }

    /** Called by SaleService when a Sales Bill records an immediate payment. */
    @Transactional
    public Receipt createSystemReceiptForSale(Sale sale) {
        Receipt receipt = Receipt.builder()
                .customer(sale.getCustomer())
                .receiptDate(sale.getSaleDate())
                .amount(sale.getPaidAmount())
                .paymentMode(sale.getPaymentMode())
                .remarks("Auto-generated on sale " + sale.getInvoiceNumber())
                .systemGenerated(true)
                .build();

        Receipt saved = receiptRepository.save(receipt);
        saved.setReceiptNumber(String.format("RCPT-%06d", saved.getId()));
        saved.addAllocation(ReceiptAllocation.builder().sale(sale).amountApplied(sale.getPaidAmount()).build());
        saved = receiptRepository.save(saved);

        ledgerService.recordReceiptCredit(saved);
        ledgerService.recordCashEntry(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Receipt receipt = findOrThrow(id);
        if (receipt.isSystemGenerated()) {
            throw new BadRequestException("This receipt was auto-generated with its sales bill. "
                    + "Delete the sales bill instead to reverse it.");
        }
        reverseAndRemove(receipt);
    }

    /** Used only by SaleService when reversing a sale that has its own auto-generated receipt. */
    @Transactional
    void deleteSystemReceipt(Long id) {
        Receipt receipt = findOrThrow(id);
        reverseAndRemove(receipt);
    }

    private void reverseAndRemove(Receipt receipt) {
        for (ReceiptAllocation allocation : receipt.getAllocations()) {
            Sale sale = allocation.getSale();
            sale.setPaidAmount(sale.getPaidAmount().subtract(allocation.getAmountApplied()));
            sale.setDueAmount(sale.getDueAmount().add(allocation.getAmountApplied()));
            sale.setPaymentStatus(derivePaymentStatus(sale.getPaidAmount(), sale.getTotalAmount()));
            saleRepository.save(sale);
        }
        String reason = (receipt.isSystemGenerated() ? "Sale reversed: " : "Receipt deleted: ")
                + receipt.getReceiptNumber();
        ledgerService.reverseReceiptCredit(receipt, reason);
        ledgerService.reverseCashEntry(receipt, reason);
        receiptRepository.delete(receipt);
    }

    private void allocate(Receipt receipt, List<ReceiptAllocationRequest> explicit, Long customerId) {
        BigDecimal remaining = receipt.getAmount();

        if (explicit != null && !explicit.isEmpty()) {
            BigDecimal totalExplicit = BigDecimal.ZERO;
            for (ReceiptAllocationRequest allocationRequest : explicit) {
                Sale sale = saleRepository.findById(allocationRequest.getSaleId())
                        .orElseThrow(() -> new SaleNotFoundException(allocationRequest.getSaleId()));
                if (sale.getCustomer() == null || !sale.getCustomer().getId().equals(customerId)) {
                    throw new BadRequestException("Sale " + sale.getInvoiceNumber() + " does not belong to this customer");
                }
                if (allocationRequest.getAmountApplied().compareTo(sale.getDueAmount()) > 0) {
                    throw new BadRequestException("Allocation of " + allocationRequest.getAmountApplied()
                            + " for " + sale.getInvoiceNumber() + " exceeds its due amount of " + sale.getDueAmount());
                }
                applyAllocation(receipt, sale, allocationRequest.getAmountApplied());
                totalExplicit = totalExplicit.add(allocationRequest.getAmountApplied());
            }
            if (totalExplicit.compareTo(receipt.getAmount()) > 0) {
                throw new BadRequestException("Total allocations (" + totalExplicit
                        + ") cannot exceed the receipt amount (" + receipt.getAmount() + ")");
            }
        } else {
            List<Sale> outstanding = saleRepository.findOutstandingByCustomer(customerId);
            for (Sale sale : outstanding) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal toApply = remaining.min(sale.getDueAmount());
                applyAllocation(receipt, sale, toApply);
                remaining = remaining.subtract(toApply);
            }
            // Any amount left over is recorded as an on-account credit against the customer
            // (still reduces overall outstanding via the ledger CREDIT entry) rather than tied to one bill.
        }
    }

    private void applyAllocation(Receipt receipt, Sale sale, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        receipt.addAllocation(ReceiptAllocation.builder().sale(sale).amountApplied(amount).build());
        sale.setPaidAmount(sale.getPaidAmount().add(amount));
        sale.setDueAmount(sale.getDueAmount().subtract(amount));
        sale.setPaymentStatus(derivePaymentStatus(sale.getPaidAmount(), sale.getTotalAmount()));
        saleRepository.save(sale);
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

    public Receipt findOrThrow(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new ReceiptNotFoundException(id));
    }
}
