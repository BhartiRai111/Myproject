package com.storehub.service;

import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.CreditNote;
import com.storehub.entity.DebitNote;
import com.storehub.entity.GstTransaction;
import com.storehub.entity.GstTransactionStatus;
import com.storehub.entity.Purchase;
import com.storehub.entity.Sale;
import com.storehub.entity.User;
import com.storehub.entity.VoucherType;
import com.storehub.repository.GstTransactionRepository;
import com.storehub.security.UserPrincipal;
import com.storehub.util.GstinValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Keeps the GST reporting dataset ({@link GstTransaction}) in sync with
 * Sale/Purchase posting and reversal. This is the ONLY place that writes to
 * {@code gst_transactions} — it never recalculates tax, it copies amounts
 * already computed and stored on the source transaction. Called from the
 * same posting/reversal call sites Phase 1/2 established
 * (SaleService.applyPostingEffects / reverseSaleEffects and their Purchase
 * equivalents) so every subsystem stays gated by one status transition.
 */
@Service
@RequiredArgsConstructor
public class GstTransactionSyncService {

    private static final DateTimeFormatter RETURN_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final GstTransactionRepository gstTransactionRepository;

    /** Creates or refreshes the ACTIVE reporting row for a posted, GST-reportable sale. A Kacchi/non-eligible sale is a no-op. */
    @Transactional
    public void syncSale(Sale sale) {
        if (!GstReportingEligibility.isEligibleForGstReporting(sale)) {
            return;
        }
        GstTransaction txn = findOrNew(VoucherType.SALE, sale.getId());
        String gstin = sale.getCustomerGstin();
        String partyName = sale.getCustomer() != null
                ? (sale.getCustomer().getFirstName() + " " + nullToEmpty(sale.getCustomer().getLastName())).trim()
                : "Walk-in Customer";

        txn.setVoucherNumber(sale.getInvoiceNumber());
        txn.setVoucherDate(sale.getSaleDate());
        txn.setPartyType(AccountingPartyType.CUSTOMER);
        txn.setPartyId(sale.getCustomer() != null ? sale.getCustomer().getId() : null);
        txn.setPartyName(partyName);
        txn.setPartyGstin(gstin);
        txn.setPlaceOfSupplyStateCode(GstinValidator.extractStateCode(gstin));
        txn.setB2b(GstinValidator.isValid(gstin));
        applyAmounts(txn, sale.getTaxableAmount(), sale.getCgstAmount(), sale.getSgstAmount(), sale.getIgstAmount(), sale.getTotalAmount());
        txn.setReturnPeriod(sale.getSaleDate().format(RETURN_PERIOD_FORMAT));
        txn.setStatus(GstTransactionStatus.ACTIVE);
        txn.setCreatedBy(currentUsername());

        gstTransactionRepository.save(txn);
    }

    /** Flips a sale's reporting row (if any) to REVERSED in place. A never-synced (Kacchi/ineligible) sale is a no-op. */
    @Transactional
    public void reverseSale(Sale sale) {
        gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.SALE, sale.getId())
                .ifPresent(txn -> {
                    txn.setStatus(GstTransactionStatus.REVERSED);
                    gstTransactionRepository.save(txn);
                });
    }

    /** Creates or refreshes the ACTIVE reporting row for a posted, GST-reportable purchase. A Kacchi/non-eligible purchase is a no-op. */
    @Transactional
    public void syncPurchase(Purchase purchase) {
        if (!GstReportingEligibility.isEligibleForGstReporting(purchase)) {
            return;
        }
        GstTransaction txn = findOrNew(VoucherType.PURCHASE, purchase.getId());
        String gstin = purchase.getSupplierGstin();
        String partyName = purchase.getSupplier() != null ? purchase.getSupplier().getName() : "Unknown Supplier";

        txn.setVoucherNumber(purchase.getPurchaseNumber());
        txn.setVoucherDate(purchase.getPurchaseDate());
        txn.setPartyType(AccountingPartyType.SUPPLIER);
        txn.setPartyId(purchase.getSupplier() != null ? purchase.getSupplier().getId() : null);
        txn.setPartyName(partyName);
        txn.setPartyGstin(gstin);
        txn.setPlaceOfSupplyStateCode(GstinValidator.extractStateCode(gstin));
        txn.setB2b(GstinValidator.isValid(gstin));
        applyAmounts(txn, purchase.getTaxableAmount(), purchase.getCgstAmount(), purchase.getSgstAmount(), purchase.getIgstAmount(), purchase.getTotalAmount());
        txn.setReturnPeriod(purchase.getPurchaseDate().format(RETURN_PERIOD_FORMAT));
        txn.setStatus(GstTransactionStatus.ACTIVE);
        txn.setCreatedBy(currentUsername());

        gstTransactionRepository.save(txn);
    }

    /** Flips a purchase's reporting row (if any) to REVERSED in place. A never-synced (Kacchi/ineligible) purchase is a no-op. */
    @Transactional
    public void reversePurchase(Purchase purchase) {
        gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.PURCHASE, purchase.getId())
                .ifPresent(txn -> {
                    txn.setStatus(GstTransactionStatus.REVERSED);
                    gstTransactionRepository.save(txn);
                });
    }

    /** Creates or refreshes the ACTIVE reporting row for a POSTED, GST-reportable Credit Note. A note against a non-eligible (Kacchi) sale is a no-op. */
    @Transactional
    public void syncCreditNote(CreditNote note) {
        if (!GstReportingEligibility.isEligibleForGstReporting(note)) {
            return;
        }
        GstTransaction txn = findOrNew(VoucherType.CREDIT_NOTE, note.getId());
        String gstin = note.getSourceSale().getCustomerGstin();
        String partyName = (note.getCustomer().getFirstName() + " " + nullToEmpty(note.getCustomer().getLastName())).trim();

        txn.setVoucherNumber(note.getVoucherNumber());
        txn.setVoucherDate(note.getNoteDate());
        txn.setPartyType(AccountingPartyType.CUSTOMER);
        txn.setPartyId(note.getCustomer().getId());
        txn.setPartyName(partyName);
        txn.setPartyGstin(gstin);
        txn.setPlaceOfSupplyStateCode(GstinValidator.extractStateCode(gstin));
        txn.setB2b(GstinValidator.isValid(gstin));
        applyAmounts(txn, note.getTaxableAmount(), note.getCgstAmount(), note.getSgstAmount(), note.getIgstAmount(), note.getTotalAmount());
        txn.setReturnPeriod(note.getNoteDate().format(RETURN_PERIOD_FORMAT));
        txn.setStatus(GstTransactionStatus.ACTIVE);
        txn.setCreatedBy(currentUsername());

        gstTransactionRepository.save(txn);
    }

    @Transactional
    public void reverseCreditNote(CreditNote note) {
        gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.CREDIT_NOTE, note.getId())
                .ifPresent(txn -> {
                    txn.setStatus(GstTransactionStatus.REVERSED);
                    gstTransactionRepository.save(txn);
                });
    }

    /** Creates or refreshes the ACTIVE reporting row for a POSTED, GST-reportable Debit Note. A note against a non-eligible (Kacchi) purchase is a no-op. */
    @Transactional
    public void syncDebitNote(DebitNote note) {
        if (!GstReportingEligibility.isEligibleForGstReporting(note)) {
            return;
        }
        GstTransaction txn = findOrNew(VoucherType.DEBIT_NOTE, note.getId());
        String gstin = note.getSourcePurchase().getSupplierGstin();
        String partyName = note.getSupplier().getName();

        txn.setVoucherNumber(note.getVoucherNumber());
        txn.setVoucherDate(note.getNoteDate());
        txn.setPartyType(AccountingPartyType.SUPPLIER);
        txn.setPartyId(note.getSupplier().getId());
        txn.setPartyName(partyName);
        txn.setPartyGstin(gstin);
        txn.setPlaceOfSupplyStateCode(GstinValidator.extractStateCode(gstin));
        txn.setB2b(GstinValidator.isValid(gstin));
        applyAmounts(txn, note.getTaxableAmount(), note.getCgstAmount(), note.getSgstAmount(), note.getIgstAmount(), note.getTotalAmount());
        txn.setReturnPeriod(note.getNoteDate().format(RETURN_PERIOD_FORMAT));
        txn.setStatus(GstTransactionStatus.ACTIVE);
        txn.setCreatedBy(currentUsername());

        gstTransactionRepository.save(txn);
    }

    @Transactional
    public void reverseDebitNote(DebitNote note) {
        gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(VoucherType.DEBIT_NOTE, note.getId())
                .ifPresent(txn -> {
                    txn.setStatus(GstTransactionStatus.REVERSED);
                    gstTransactionRepository.save(txn);
                });
    }

    private GstTransaction findOrNew(VoucherType type, Long sourceId) {
        Optional<GstTransaction> existing = gstTransactionRepository.findBySourceTransactionTypeAndSourceTransactionId(type, sourceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        GstTransaction txn = new GstTransaction();
        txn.setSourceTransactionType(type);
        txn.setSourceTransactionId(sourceId);
        return txn;
    }

    private void applyAmounts(GstTransaction txn, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal totalValue) {
        BigDecimal taxableAmount = nullToZero(taxable);
        BigDecimal cgstAmount = nullToZero(cgst);
        BigDecimal sgstAmount = nullToZero(sgst);
        BigDecimal igstAmount = nullToZero(igst);
        txn.setTaxableAmount(taxableAmount);
        txn.setCgstAmount(cgstAmount);
        txn.setSgstAmount(sgstAmount);
        txn.setIgstAmount(igstAmount);
        txn.setTotalTax(cgstAmount.add(sgstAmount).add(igstAmount));
        txn.setTotalValue(nullToZero(totalValue));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            User user = principal.getUser();
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            return (user.getFirstName() + " " + lastName).trim();
        }
        return "System";
    }
}
