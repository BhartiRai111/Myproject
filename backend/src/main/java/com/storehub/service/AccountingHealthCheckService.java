package com.storehub.service;

import com.storehub.dto.AccountingHealthCheckResponse;
import com.storehub.dto.HealthCheckFinding;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.HealthCheckStatus;
import com.storehub.entity.VoucherType;
import com.storehub.repository.CustomerLedgerEntryRepository;
import com.storehub.repository.JournalDetailRepository;
import com.storehub.repository.JournalHeaderRepository;
import com.storehub.repository.PaymentRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.ReceiptRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.repository.SupplierLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accounting Reconciliation / Health Check (spec section 32). Every check
 * reports PASS/WARNING/ERROR explicitly rather than hiding a mismatch — see
 * each method's Javadoc for what ERROR vs. WARNING means for that check.
 */
@Service
@RequiredArgsConstructor
public class AccountingHealthCheckService {

    private final JournalHeaderRepository journalHeaderRepository;
    private final JournalDetailRepository journalDetailRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerLedgerEntryRepository customerLedgerEntryRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;

    @Transactional(readOnly = true)
    public AccountingHealthCheckResponse runHealthCheck() {
        List<HealthCheckFinding> findings = new ArrayList<>();
        findings.add(checkJournalBalance());
        findings.add(checkDuplicatePosting());
        findings.add(checkSourcePosting());
        findings.add(checkOrphanJournals());
        findings.add(checkLedgerMismatch());

        HealthCheckStatus overall = HealthCheckStatus.PASS;
        for (HealthCheckFinding f : findings) {
            if (f.getStatus() == HealthCheckStatus.ERROR) {
                overall = HealthCheckStatus.ERROR;
                break;
            }
            if (f.getStatus() == HealthCheckStatus.WARNING && overall != HealthCheckStatus.ERROR) {
                overall = HealthCheckStatus.WARNING;
            }
        }

        return AccountingHealthCheckResponse.builder()
                .generatedAt(LocalDateTime.now())
                .overallStatus(overall)
                .findings(findings)
                .build();
    }

    /** ERROR if any journal's own lines don't sum debit=credit — should be impossible given AccountingService's posting-time validation. */
    private HealthCheckFinding checkJournalBalance() {
        List<Object[]> unbalanced = journalDetailRepository.findUnbalancedJournals();
        if (unbalanced.isEmpty()) {
            return pass("Journal Balance", "Every journal's total debit equals its total credit.");
        }
        List<String> details = new ArrayList<>();
        for (Object[] r : unbalanced) {
            details.add("Journal " + r[1] + " (id=" + r[0] + "): debit=" + r[2] + " credit=" + r[3]);
        }
        return error("Journal Balance", unbalanced.size() + " journal(s) do not balance.", details);
    }

    /** ERROR if the same (voucherType, voucherId) has more than one currently-active posted journal — should be impossible given the duplicate-post guard. */
    private HealthCheckFinding checkDuplicatePosting() {
        List<Object[]> duplicates = journalHeaderRepository.findDuplicateActivePostings();
        if (duplicates.isEmpty()) {
            return pass("Duplicate Posting", "No source transaction has more than one active posted journal.");
        }
        List<String> details = new ArrayList<>();
        for (Object[] r : duplicates) {
            details.add(r[0] + " voucherId=" + r[1] + ": " + r[2] + " active journals");
        }
        return error("Duplicate Posting", duplicates.size() + " source transaction(s) have duplicate active journals.", details);
    }

    /** WARNING for any POSTED Sale/Purchase or any Receipt/Payment missing its expected active accounting journal. */
    private HealthCheckFinding checkSourcePosting() {
        List<String> missing = new ArrayList<>();

        Set<Long> saleJournalIds = new HashSet<>(journalHeaderRepository.findActivePostedVoucherIds(VoucherType.SALE));
        for (Long id : saleRepository.findCompletedIds()) {
            if (!saleJournalIds.contains(id)) {
                missing.add("SALE id=" + id + " is POSTED but has no active accounting journal");
            }
        }

        Set<Long> purchaseJournalIds = new HashSet<>(journalHeaderRepository.findActivePostedVoucherIds(VoucherType.PURCHASE));
        for (Long id : purchaseRepository.findCompletedIds()) {
            if (!purchaseJournalIds.contains(id)) {
                missing.add("PURCHASE id=" + id + " is POSTED but has no active accounting journal");
            }
        }

        Set<Long> receiptJournalIds = new HashSet<>(journalHeaderRepository.findActivePostedVoucherIds(VoucherType.RECEIPT));
        for (Long id : receiptRepository.findAllIds()) {
            if (!receiptJournalIds.contains(id)) {
                missing.add("RECEIPT id=" + id + " has no active accounting journal");
            }
        }

        Set<Long> paymentJournalIds = new HashSet<>(journalHeaderRepository.findActivePostedVoucherIds(VoucherType.PAYMENT));
        for (Long id : paymentRepository.findAllIds()) {
            if (!paymentJournalIds.contains(id)) {
                missing.add("PAYMENT id=" + id + " has no active accounting journal");
            }
        }

        if (missing.isEmpty()) {
            return pass("Source Posting", "Every POSTED Sale/Purchase and every Receipt/Payment has its expected accounting journal.");
        }
        return warning("Source Posting", missing.size() + " source transaction(s) are missing their expected accounting journal.", missing);
    }

    /**
     * WARNING for a journal whose source Sale/Purchase/Receipt/Payment row no longer exists. This is
     * reachable legitimately: {@code SaleService.deleteSale}/{@code PurchaseService.deletePurchase} reverse
     * the journal (which correctly stays, per audit design) and then hard-delete the source row — so a
     * "delete" (as opposed to "cancel") of an old bill will always show up here. That is expected, not a bug;
     * the check still surfaces it rather than hiding it, per the spec's explicit instruction.
     */
    private HealthCheckFinding checkOrphanJournals() {
        List<String> orphans = new ArrayList<>();

        Set<Long> saleIds = new HashSet<>(saleRepository.findAllIds());
        for (Long voucherId : journalHeaderRepository.findDistinctVoucherIds(VoucherType.SALE)) {
            if (!saleIds.contains(voucherId)) {
                orphans.add("Journal(s) reference SALE id=" + voucherId + ", which no longer exists");
            }
        }

        Set<Long> purchaseIds = new HashSet<>(purchaseRepository.findAllIds());
        for (Long voucherId : journalHeaderRepository.findDistinctVoucherIds(VoucherType.PURCHASE)) {
            if (!purchaseIds.contains(voucherId)) {
                orphans.add("Journal(s) reference PURCHASE id=" + voucherId + ", which no longer exists");
            }
        }

        Set<Long> receiptIds = new HashSet<>(receiptRepository.findAllIds());
        for (Long voucherId : journalHeaderRepository.findDistinctVoucherIds(VoucherType.RECEIPT)) {
            if (!receiptIds.contains(voucherId)) {
                orphans.add("Journal(s) reference RECEIPT id=" + voucherId + ", which no longer exists");
            }
        }

        Set<Long> paymentIds = new HashSet<>(paymentRepository.findAllIds());
        for (Long voucherId : journalHeaderRepository.findDistinctVoucherIds(VoucherType.PAYMENT)) {
            if (!paymentIds.contains(voucherId)) {
                orphans.add("Journal(s) reference PAYMENT id=" + voucherId + ", which no longer exists");
            }
        }

        if (orphans.isEmpty()) {
            return pass("Orphan Journal", "Every journal's source reference still exists.");
        }
        return warning("Orphan Journal", orphans.size() + " journal(s) reference a source transaction that no longer exists.", orphans);
    }

    /** WARNING where the operational CustomerLedgerEntry/SupplierLedgerEntry outstanding disagrees with the accounting journal's control-account balance for the same party. */
    private HealthCheckFinding checkLedgerMismatch() {
        List<String> mismatches = new ArrayList<>();

        Map<Long, BigDecimal> journalByCustomer = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumByParty(AccountingPartyType.CUSTOMER)) {
            journalByCustomer.put((Long) r[0], ((BigDecimal) r[1]).subtract((BigDecimal) r[2]));
        }
        for (Object[] r : customerLedgerEntryRepository.sumByCustomer()) {
            Long customerId = (Long) r[0];
            BigDecimal ledgerBalance = (BigDecimal) r[1];
            BigDecimal journalBalance = journalByCustomer.getOrDefault(customerId, BigDecimal.ZERO);
            if (ledgerBalance.compareTo(journalBalance) != 0) {
                mismatches.add("Customer id=" + customerId + ": operational ledger=" + ledgerBalance + " vs accounting journal=" + journalBalance);
            }
        }

        Map<Long, BigDecimal> journalBySupplier = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumByParty(AccountingPartyType.SUPPLIER)) {
            journalBySupplier.put((Long) r[0], ((BigDecimal) r[2]).subtract((BigDecimal) r[1]));
        }
        for (Object[] r : supplierLedgerEntryRepository.sumBySupplier()) {
            Long supplierId = (Long) r[0];
            BigDecimal ledgerBalance = (BigDecimal) r[1];
            BigDecimal journalBalance = journalBySupplier.getOrDefault(supplierId, BigDecimal.ZERO);
            if (ledgerBalance.compareTo(journalBalance) != 0) {
                mismatches.add("Supplier id=" + supplierId + ": operational ledger=" + ledgerBalance + " vs accounting journal=" + journalBalance);
            }
        }

        if (mismatches.isEmpty()) {
            return pass("Party Ledger Match", "Every customer/supplier's operational ledger balance matches the accounting journal.");
        }
        return warning("Party Ledger Match", mismatches.size() + " part(y/ies) have a mismatch between the operational ledger and the accounting journal.", mismatches);
    }

    private HealthCheckFinding pass(String name, String message) {
        return HealthCheckFinding.builder().checkName(name).status(HealthCheckStatus.PASS).message(message).details(List.of()).build();
    }

    private HealthCheckFinding warning(String name, String message, List<String> details) {
        return HealthCheckFinding.builder().checkName(name).status(HealthCheckStatus.WARNING).message(message).details(details).build();
    }

    private HealthCheckFinding error(String name, String message, List<String> details) {
        return HealthCheckFinding.builder().checkName(name).status(HealthCheckStatus.ERROR).message(message).details(details).build();
    }
}
