package com.storehub.service;

import com.storehub.entity.*;
import com.storehub.repository.CashLedgerEntryRepository;
import com.storehub.repository.CustomerLedgerEntryRepository;
import com.storehub.repository.GstEntryRepository;
import com.storehub.repository.PurchaseGstEntryRepository;
import com.storehub.repository.SupplierLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Shared posting logic for the customer receivable ledger, output GST log,
 * and cash/bank/UPI movement log. Every posting is an immutable row; a
 * reversal is a new offsetting row (never a delete/update of the original),
 * matching the StockHistory convention already used for stock movements.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final CustomerLedgerEntryRepository customerLedgerEntryRepository;
    private final GstEntryRepository gstEntryRepository;
    private final CashLedgerEntryRepository cashLedgerEntryRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    private final PurchaseGstEntryRepository purchaseGstEntryRepository;

    @Transactional
    public void recordSaleDebit(Sale sale) {
        if (sale.getCustomer() == null) {
            return;
        }
        customerLedgerEntryRepository.save(CustomerLedgerEntry.builder()
                .customer(sale.getCustomer())
                .entryType(LedgerEntryType.DEBIT)
                .amount(sale.getTotalAmount())
                .referenceType(LedgerReferenceType.SALE)
                .referenceId(sale.getId())
                .description("Sale " + sale.getInvoiceNumber())
                .entryDate(sale.getSaleDate())
                .build());
    }

    @Transactional
    public void reverseSaleDebit(Sale sale, String reason) {
        if (sale.getCustomer() == null) {
            return;
        }
        customerLedgerEntryRepository.save(CustomerLedgerEntry.builder()
                .customer(sale.getCustomer())
                .entryType(LedgerEntryType.CREDIT)
                .amount(sale.getTotalAmount())
                .referenceType(LedgerReferenceType.SALE)
                .referenceId(sale.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    @Transactional
    public void recordGstEntry(Sale sale) {
        if (sale.getGstType() != GstType.GST) {
            return;
        }
        gstEntryRepository.save(GstEntry.builder()
                .saleId(sale.getId())
                .taxableAmount(sale.getTaxableAmount())
                .cgstAmount(sale.getCgstAmount())
                .sgstAmount(sale.getSgstAmount())
                .igstAmount(sale.getIgstAmount())
                .entryDate(sale.getSaleDate())
                .build());
    }

    @Transactional
    public void reverseGstEntry(Sale sale) {
        if (sale.getGstType() != GstType.GST) {
            return;
        }
        gstEntryRepository.save(GstEntry.builder()
                .saleId(sale.getId())
                .taxableAmount(sale.getTaxableAmount().negate())
                .cgstAmount(sale.getCgstAmount().negate())
                .sgstAmount(sale.getSgstAmount().negate())
                .igstAmount(sale.getIgstAmount().negate())
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    @Transactional
    public void recordReceiptCredit(Receipt receipt) {
        customerLedgerEntryRepository.save(CustomerLedgerEntry.builder()
                .customer(receipt.getCustomer())
                .entryType(LedgerEntryType.CREDIT)
                .amount(receipt.getAmount())
                .referenceType(LedgerReferenceType.RECEIPT)
                .referenceId(receipt.getId())
                .description("Receipt " + receipt.getReceiptNumber())
                .entryDate(receipt.getReceiptDate())
                .build());
    }

    @Transactional
    public void reverseReceiptCredit(Receipt receipt, String reason) {
        customerLedgerEntryRepository.save(CustomerLedgerEntry.builder()
                .customer(receipt.getCustomer())
                .entryType(LedgerEntryType.DEBIT)
                .amount(receipt.getAmount())
                .referenceType(LedgerReferenceType.RECEIPT)
                .referenceId(receipt.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    @Transactional
    public void recordCashEntry(Receipt receipt) {
        cashLedgerEntryRepository.save(CashLedgerEntry.builder()
                .paymentMode(receipt.getPaymentMode())
                .amount(receipt.getAmount())
                .referenceType(LedgerReferenceType.RECEIPT)
                .referenceId(receipt.getId())
                .description("Receipt " + receipt.getReceiptNumber())
                .entryDate(receipt.getReceiptDate())
                .build());
    }

    @Transactional
    public void reverseCashEntry(Receipt receipt, String reason) {
        cashLedgerEntryRepository.save(CashLedgerEntry.builder()
                .paymentMode(receipt.getPaymentMode())
                .amount(receipt.getAmount().negate())
                .referenceType(LedgerReferenceType.RECEIPT)
                .referenceId(receipt.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    /** Used for a walk-in sale (no customer) paid immediately: money still moved, but there is no receivable to track. */
    @Transactional
    public void recordCashEntryForSale(Sale sale) {
        if (sale.getPaidAmount() == null || sale.getPaidAmount().signum() <= 0) {
            return;
        }
        cashLedgerEntryRepository.save(CashLedgerEntry.builder()
                .paymentMode(sale.getPaymentMode())
                .amount(sale.getPaidAmount())
                .referenceType(LedgerReferenceType.SALE)
                .referenceId(sale.getId())
                .description("Sale " + sale.getInvoiceNumber())
                .entryDate(sale.getSaleDate())
                .build());
    }

    @Transactional
    public void reverseCashEntryForSale(Sale sale, String reason) {
        if (sale.getPaidAmount() == null || sale.getPaidAmount().signum() <= 0) {
            return;
        }
        cashLedgerEntryRepository.save(CashLedgerEntry.builder()
                .paymentMode(sale.getPaymentMode())
                .amount(sale.getPaidAmount().negate())
                .referenceType(LedgerReferenceType.SALE)
                .referenceId(sale.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    public BigDecimal getOutstandingForCustomer(Long customerId) {
        return customerLedgerEntryRepository.getOutstandingForCustomer(customerId);
    }

    public BigDecimal getTotalOutstanding() {
        return customerLedgerEntryRepository.getTotalOutstanding();
    }

    // ---- Supplier payable ledger, input GST log, and cash outflow (purchase side) ----

    @Transactional
    public void recordPurchaseCredit(Purchase purchase) {
        supplierLedgerEntryRepository.save(SupplierLedgerEntry.builder()
                .supplier(purchase.getSupplier())
                .entryType(LedgerEntryType.CREDIT)
                .amount(purchase.getTotalAmount())
                .referenceType(LedgerReferenceType.PURCHASE)
                .referenceId(purchase.getId())
                .description("Purchase " + purchase.getPurchaseNumber())
                .entryDate(purchase.getPurchaseDate())
                .build());
    }

    @Transactional
    public void reversePurchaseCredit(Purchase purchase, String reason) {
        supplierLedgerEntryRepository.save(SupplierLedgerEntry.builder()
                .supplier(purchase.getSupplier())
                .entryType(LedgerEntryType.DEBIT)
                .amount(purchase.getTotalAmount())
                .referenceType(LedgerReferenceType.PURCHASE)
                .referenceId(purchase.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    @Transactional
    public void recordInputGstEntry(Purchase purchase) {
        if (purchase.getGstType() != GstType.GST) {
            return;
        }
        purchaseGstEntryRepository.save(PurchaseGstEntry.builder()
                .purchaseId(purchase.getId())
                .taxableAmount(purchase.getTaxableAmount())
                .cgstAmount(purchase.getCgstAmount())
                .sgstAmount(purchase.getSgstAmount())
                .igstAmount(purchase.getIgstAmount())
                .entryDate(purchase.getPurchaseDate())
                .build());
    }

    @Transactional
    public void reverseInputGstEntry(Purchase purchase) {
        if (purchase.getGstType() != GstType.GST) {
            return;
        }
        purchaseGstEntryRepository.save(PurchaseGstEntry.builder()
                .purchaseId(purchase.getId())
                .taxableAmount(purchase.getTaxableAmount().negate())
                .cgstAmount(purchase.getCgstAmount().negate())
                .sgstAmount(purchase.getSgstAmount().negate())
                .igstAmount(purchase.getIgstAmount().negate())
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    @Transactional
    public void recordPaymentDebit(Payment payment) {
        supplierLedgerEntryRepository.save(SupplierLedgerEntry.builder()
                .supplier(payment.getSupplier())
                .entryType(LedgerEntryType.DEBIT)
                .amount(payment.getAmount())
                .referenceType(LedgerReferenceType.PAYMENT)
                .referenceId(payment.getId())
                .description("Payment " + payment.getPaymentNumber())
                .entryDate(payment.getPaymentDate())
                .build());
    }

    @Transactional
    public void reversePaymentDebit(Payment payment, String reason) {
        supplierLedgerEntryRepository.save(SupplierLedgerEntry.builder()
                .supplier(payment.getSupplier())
                .entryType(LedgerEntryType.CREDIT)
                .amount(payment.getAmount())
                .referenceType(LedgerReferenceType.PAYMENT)
                .referenceId(payment.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    /** Cash/bank/UPI outflow for a manual Payment Entry. */
    @Transactional
    public void recordCashEntryOut(Payment payment) {
        cashLedgerEntryRepository.save(CashLedgerEntry.builder()
                .paymentMode(payment.getPaymentMode())
                .amount(payment.getAmount().negate())
                .referenceType(LedgerReferenceType.PAYMENT)
                .referenceId(payment.getId())
                .description("Payment " + payment.getPaymentNumber())
                .entryDate(payment.getPaymentDate())
                .build());
    }

    @Transactional
    public void reverseCashEntryOut(Payment payment, String reason) {
        cashLedgerEntryRepository.save(CashLedgerEntry.builder()
                .paymentMode(payment.getPaymentMode())
                .amount(payment.getAmount())
                .referenceType(LedgerReferenceType.PAYMENT)
                .referenceId(payment.getId())
                .description(reason)
                .entryDate(java.time.LocalDate.now())
                .build());
    }

    public BigDecimal getOutstandingForSupplier(Long supplierId) {
        return supplierLedgerEntryRepository.getOutstandingForSupplier(supplierId);
    }

    public BigDecimal getTotalPayables() {
        return supplierLedgerEntryRepository.getTotalOutstanding();
    }
}
