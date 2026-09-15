package com.storehub.service;

import com.storehub.dto.JournalCreateRequest;
import com.storehub.dto.JournalHeaderResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Account;
import com.storehub.entity.AccountingPartyType;
import com.storehub.entity.GstType;
import com.storehub.entity.JournalDetail;
import com.storehub.entity.JournalHeader;
import com.storehub.entity.JournalStatus;
import com.storehub.entity.Payment;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.Purchase;
import com.storehub.entity.Receipt;
import com.storehub.entity.Sale;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.User;
import com.storehub.entity.VoucherType;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.JournalNotFoundException;
import com.storehub.repository.JournalHeaderRepository;
import com.storehub.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The central double-entry posting engine. Every POSTED financial
 * transaction (Sale/Purchase/Receipt/Payment) calls exactly one
 * {@code post*Journal} method here, alongside its existing
 * {@code LedgerService} call — this is a parallel financial ledger, not a
 * replacement for CustomerLedgerEntry/SupplierLedgerEntry/CashLedgerEntry/
 * GstEntry/PurchaseGstEntry, which remain the operational sub-ledgers and
 * the source of the already-calculated amounts posted here. GST amounts,
 * discounts, and taxable values are consumed as-is, never recomputed.
 */
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final AccountService accountService;
    private final JournalHeaderRepository journalHeaderRepository;

    // ---- Sale ----

    /**
     * Books the receivable/cash side of a Sale against Sales + Output GST.
     * A sale with a named customer always books the full total to Customer
     * Receivable, regardless of how much is paid immediately (matching
     * LedgerService.recordSaleDebit's unconditional behaviour); the
     * receivable is then cleared by the Receipt's own journal. A walk-in
     * sale (no customer) instead books the paid amount directly to
     * Cash/Bank, since the operational ledger tracks no receivable for an
     * unidentified customer; if nothing was paid on a walk-in sale, there
     * is nothing to post (matching the existing ledger's own gap here).
     */
    @Transactional
    public void postSaleJournal(Sale sale) {
        List<JournalLine> lines = new ArrayList<>();
        if (sale.getCustomer() != null) {
            lines.add(JournalLine.debit(SystemAccountCode.CUSTOMER_RECEIVABLE, sale.getTotalAmount(),
                    AccountingPartyType.CUSTOMER, sale.getCustomer().getId()));
            addOutputSalesCreditLines(lines, sale);
        } else if (sale.getPaidAmount() != null && sale.getPaidAmount().signum() > 0) {
            lines.add(JournalLine.debit(resolveCashOrBank(sale.getPaymentMode()), sale.getPaidAmount()));
            addOutputSalesCreditLines(lines, sale);
        } else {
            return;
        }
        postJournal(VoucherType.SALE, sale.getId(), sale.getInvoiceNumber(), sale.getSaleDate(),
                "Sale " + sale.getInvoiceNumber(), lines);
    }

    private void addOutputSalesCreditLines(List<JournalLine> lines, Sale sale) {
        lines.add(JournalLine.credit(SystemAccountCode.SALES, sale.getTaxableAmount()));
        if (sale.getGstType() == GstType.GST) {
            addIfPositive(lines, SystemAccountCode.OUTPUT_CGST, sale.getCgstAmount(), false);
            addIfPositive(lines, SystemAccountCode.OUTPUT_SGST, sale.getSgstAmount(), false);
            addIfPositive(lines, SystemAccountCode.OUTPUT_IGST, sale.getIgstAmount(), false);
        }
    }

    @Transactional
    public void reverseSaleJournal(Sale sale, String reason) {
        reverseJournal(VoucherType.SALE, sale.getId(), reason);
    }

    // ---- Purchase ----

    /** Always books the full payable, regardless of how much is paid immediately; the Payment's own journal clears it. */
    @Transactional
    public void postPurchaseJournal(Purchase purchase) {
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(SystemAccountCode.PURCHASE, purchase.getTaxableAmount()));
        if (purchase.getGstType() == GstType.GST) {
            addIfPositive(lines, SystemAccountCode.INPUT_CGST, purchase.getCgstAmount(), true);
            addIfPositive(lines, SystemAccountCode.INPUT_SGST, purchase.getSgstAmount(), true);
            addIfPositive(lines, SystemAccountCode.INPUT_IGST, purchase.getIgstAmount(), true);
        }
        lines.add(JournalLine.credit(SystemAccountCode.SUPPLIER_PAYABLE, purchase.getTotalAmount(),
                AccountingPartyType.SUPPLIER, purchase.getSupplier().getId()));

        postJournal(VoucherType.PURCHASE, purchase.getId(), purchase.getPurchaseNumber(), purchase.getPurchaseDate(),
                "Purchase " + purchase.getPurchaseNumber(), lines);
    }

    @Transactional
    public void reversePurchaseJournal(Purchase purchase, String reason) {
        reverseJournal(VoucherType.PURCHASE, purchase.getId(), reason);
    }

    // ---- Receipt ----

    @Transactional
    public void postReceiptJournal(Receipt receipt) {
        List<JournalLine> lines = List.of(
                JournalLine.debit(resolveCashOrBank(receipt.getPaymentMode()), receipt.getAmount()),
                JournalLine.credit(SystemAccountCode.CUSTOMER_RECEIVABLE, receipt.getAmount(),
                        AccountingPartyType.CUSTOMER, receipt.getCustomer().getId()));

        postJournal(VoucherType.RECEIPT, receipt.getId(), receipt.getReceiptNumber(), receipt.getReceiptDate(),
                "Receipt " + receipt.getReceiptNumber(), lines);
    }

    @Transactional
    public void reverseReceiptJournal(Receipt receipt, String reason) {
        reverseJournal(VoucherType.RECEIPT, receipt.getId(), reason);
    }

    // ---- Payment ----

    @Transactional
    public void postPaymentJournal(Payment payment) {
        List<JournalLine> lines = List.of(
                JournalLine.debit(SystemAccountCode.SUPPLIER_PAYABLE, payment.getAmount(),
                        AccountingPartyType.SUPPLIER, payment.getSupplier().getId()),
                JournalLine.credit(resolveCashOrBank(payment.getPaymentMode()), payment.getAmount()));

        postJournal(VoucherType.PAYMENT, payment.getId(), payment.getPaymentNumber(), payment.getPaymentDate(),
                "Payment " + payment.getPaymentNumber(), lines);
    }

    @Transactional
    public void reversePaymentJournal(Payment payment, String reason) {
        reverseJournal(VoucherType.PAYMENT, payment.getId(), reason);
    }

    // ---- Read ----

    @Transactional(readOnly = true)
    public JournalHeaderResponse getJournalById(Long id) {
        JournalHeader header = journalHeaderRepository.findById(id)
                .orElseThrow(() -> new JournalNotFoundException(id));
        return JournalHeaderResponse.fromEntity(header);
    }

    @Transactional(readOnly = true)
    public PagedResponse<JournalHeaderResponse> searchJournals(VoucherType voucherType, LocalDate fromDate, LocalDate toDate,
                                                                 String search, int page, int size) {
        return searchJournals(voucherType, null, fromDate, toDate, search, page, size);
    }

    /** Journal Register: same search, with an optional status filter (DRAFT/POSTED/REVERSED). */
    public PagedResponse<JournalHeaderResponse> searchJournals(VoucherType voucherType, JournalStatus status, LocalDate fromDate, LocalDate toDate,
                                                                 String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("journalDate").descending().and(Sort.by("id").descending()));
        Page<JournalHeaderResponse> result = journalHeaderRepository.search(voucherType, status, fromDate, toDate, search, pageable)
                .map(JournalHeaderResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    // ---- Core engine ----

    private void addIfPositive(List<JournalLine> lines, SystemAccountCode account, BigDecimal amount, boolean debit) {
        if (amount != null && amount.signum() > 0) {
            lines.add(debit ? JournalLine.debit(account, amount) : JournalLine.credit(account, amount));
        }
    }

    private SystemAccountCode resolveCashOrBank(PaymentMode mode) {
        return mode == PaymentMode.CASH ? SystemAccountCode.CASH : SystemAccountCode.BANK;
    }

    /**
     * Validates and posts a balanced journal for one voucher. Rejects a
     * second POSTED original for the same (voucherType, voucherId) so a
     * Sale/Purchase/Receipt/Payment can never be journalled twice.
     */
    @Transactional
    public JournalHeader postJournal(VoucherType voucherType, Long voucherId, String voucherNumber,
                                      LocalDate journalDate, String narration, List<JournalLine> lines) {
        validateJournal(lines);
        List<ResolvedLine> resolved = lines.stream()
                .map(line -> new ResolvedLine(resolveActiveAccount(line.account()), line.debitAmount(), line.creditAmount(),
                        line.narration(), line.partyType(), line.partyId()))
                .toList();
        return buildAndSaveJournal(voucherType, voucherId, voucherNumber, journalDate, narration, resolved);
    }

    /**
     * Posts a manual journal entry (voucher type JOURNAL) from the accounting
     * API, e.g. adjustment entries not tied to a Sale/Purchase/Receipt/Payment.
     * Lines reference accounts by id (the caller picks real accounts from the
     * Chart of Accounts), not by {@link SystemAccountCode}. Manual journals
     * carry no source voucherId, so duplicate-posting protection does not
     * apply to them.
     */
    @Transactional
    public JournalHeader postManualJournal(LocalDate journalDate, String narration, List<ManualJournalLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("A journal must have at least one line");
        }
        List<ResolvedLine> resolved = lines.stream()
                .map(line -> new ResolvedLine(resolveActiveAccountById(line.accountId()),
                        line.debitAmount() != null ? line.debitAmount() : BigDecimal.ZERO,
                        line.creditAmount() != null ? line.creditAmount() : BigDecimal.ZERO,
                        line.narration(), line.partyType(), line.partyId()))
                .toList();
        validateResolvedLines(resolved);
        return buildAndSaveJournal(VoucherType.JOURNAL, null, null, journalDate, narration, resolved);
    }

    /** Convenience overload for the REST layer: maps the API request DTO and returns the response DTO. */
    @Transactional
    public JournalHeaderResponse postManualJournal(JournalCreateRequest request) {
        List<ManualJournalLine> lines = request.getLines().stream()
                .map(line -> new ManualJournalLine(line.getAccountId(), line.getDebitAmount(), line.getCreditAmount(),
                        line.getNarration(), line.getPartyType(), line.getPartyId()))
                .toList();
        JournalHeader saved = postManualJournal(request.getJournalDate(), request.getNarration(), lines);
        return JournalHeaderResponse.fromEntity(saved);
    }

    private record ResolvedLine(Account account, BigDecimal debitAmount, BigDecimal creditAmount,
                                 String narration, AccountingPartyType partyType, Long partyId) {
    }

    private JournalHeader buildAndSaveJournal(VoucherType voucherType, Long voucherId, String voucherNumber,
                                               LocalDate journalDate, String narration, List<ResolvedLine> lines) {
        if (voucherId != null && journalHeaderRepository
                .existsByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(voucherType, voucherId, JournalStatus.POSTED)) {
            throw new BadRequestException("An accounting journal has already been posted for this " + voucherType.name().toLowerCase());
        }

        String user = currentUsername();
        JournalHeader header = JournalHeader.builder()
                .journalDate(journalDate)
                .voucherType(voucherType)
                .voucherId(voucherId)
                .voucherNumber(voucherNumber)
                .narration(narration)
                .status(JournalStatus.POSTED)
                .postedBy(user)
                .postedAt(LocalDateTime.now())
                .createdBy(user)
                .build();

        for (ResolvedLine line : lines) {
            header.addLine(JournalDetail.builder()
                    .account(line.account())
                    .debitAmount(line.debitAmount())
                    .creditAmount(line.creditAmount())
                    .narration(line.narration() != null ? line.narration() : narration)
                    .partyType(line.partyType())
                    .partyId(line.partyId())
                    .referenceType(voucherType)
                    .referenceId(voucherId)
                    .build());
        }

        JournalHeader saved = journalHeaderRepository.save(header);
        saved.setJournalNumber(String.format("JRN-%06d", saved.getId()));
        saved = journalHeaderRepository.save(saved);
        if (saved.getVoucherNumber() == null) {
            saved.setVoucherNumber(saved.getJournalNumber());
            saved = journalHeaderRepository.save(saved);
        }
        return saved;
    }

    /**
     * Posts a reversal journal that offsets the currently active original
     * posting for (voucherType, voucherId), and marks that original
     * REVERSED. A no-op if nothing was ever posted for this voucher
     * (e.g. it was never COMPLETED) or it was already reversed.
     */
    @Transactional
    public void reverseJournal(VoucherType voucherType, Long voucherId, String reason) {
        if (voucherId == null) {
            return;
        }
        Optional<JournalHeader> originalOpt = journalHeaderRepository
                .findByVoucherTypeAndVoucherIdAndStatusAndReversalOfJournalIsNull(voucherType, voucherId, JournalStatus.POSTED);
        if (originalOpt.isEmpty()) {
            return;
        }
        JournalHeader original = originalOpt.get();

        String user = currentUsername();
        JournalHeader reversal = JournalHeader.builder()
                .journalDate(LocalDate.now())
                .voucherType(original.getVoucherType())
                .voucherId(original.getVoucherId())
                .voucherNumber(original.getVoucherNumber())
                .narration(reason)
                .status(JournalStatus.POSTED)
                .reversalOfJournal(original)
                .postedBy(user)
                .postedAt(LocalDateTime.now())
                .createdBy(user)
                .build();

        for (JournalDetail originalLine : original.getLines()) {
            reversal.addLine(JournalDetail.builder()
                    .account(originalLine.getAccount())
                    .debitAmount(originalLine.getCreditAmount())
                    .creditAmount(originalLine.getDebitAmount())
                    .narration(reason)
                    .partyType(originalLine.getPartyType())
                    .partyId(originalLine.getPartyId())
                    .referenceType(voucherType)
                    .referenceId(voucherId)
                    .build());
        }

        JournalHeader savedReversal = journalHeaderRepository.save(reversal);
        savedReversal.setJournalNumber(String.format("JRN-%06d", savedReversal.getId()));
        journalHeaderRepository.save(savedReversal);

        original.setStatus(JournalStatus.REVERSED);
        journalHeaderRepository.save(original);
    }

    private void validateJournal(List<JournalLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("A journal must have at least one line");
        }
        validateAmounts(lines.stream()
                .map(line -> new BigDecimal[]{
                        line.debitAmount() != null ? line.debitAmount() : BigDecimal.ZERO,
                        line.creditAmount() != null ? line.creditAmount() : BigDecimal.ZERO})
                .toList());
    }

    private void validateResolvedLines(List<ResolvedLine> lines) {
        if (lines.isEmpty()) {
            throw new BadRequestException("A journal must have at least one line");
        }
        validateAmounts(lines.stream()
                .map(line -> new BigDecimal[]{line.debitAmount(), line.creditAmount()})
                .toList());
    }

    /** Shared debit/credit validation: no negative amounts, no line with both, at least one of each, and total debit = total credit. */
    private void validateAmounts(List<BigDecimal[]> debitCreditPairs) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        boolean hasDebit = false;
        boolean hasCredit = false;

        for (BigDecimal[] pair : debitCreditPairs) {
            BigDecimal debit = pair[0];
            BigDecimal credit = pair[1];

            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new BadRequestException("A journal line amount cannot be negative");
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new BadRequestException("A journal line cannot have both a debit and a credit amount");
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new BadRequestException("A journal line must have either a debit or a credit amount");
            }

            hasDebit = hasDebit || debit.signum() > 0;
            hasCredit = hasCredit || credit.signum() > 0;
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        if (!hasDebit || !hasCredit) {
            throw new BadRequestException("A journal must have at least one debit line and one credit line");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BadRequestException("Journal is not balanced: total debit (" + totalDebit
                    + ") does not equal total credit (" + totalCredit + ")");
        }
    }

    private Account resolveActiveAccount(SystemAccountCode code) {
        Account account = accountService.getSystemAccount(code);
        if (!account.isActive()) {
            throw new BadRequestException("Account '" + account.getAccountName() + "' is inactive and cannot be posted to");
        }
        return account;
    }

    private Account resolveActiveAccountById(Long accountId) {
        Account account = accountService.findOrThrow(accountId);
        if (!account.isActive()) {
            throw new BadRequestException("Account '" + account.getAccountName() + "' is inactive and cannot be posted to");
        }
        return account;
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
