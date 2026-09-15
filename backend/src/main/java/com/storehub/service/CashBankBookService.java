package com.storehub.service;

import com.storehub.dto.CashBankBookResponse;
import com.storehub.dto.CashBankBookRow;
import com.storehub.entity.Account;
import com.storehub.entity.JournalDetail;
import com.storehub.entity.JournalHeader;
import com.storehub.entity.LedgerEntryType;
import com.storehub.entity.SystemAccountCode;
import com.storehub.repository.JournalDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cash Book and Bank Book (spec sections 9-10): a debit into the CASH/BANK
 * system account is a receipt, a credit out of it is a payment — read
 * straight off the accounting journal, the same source of truth as the
 * Account Ledger, just reshaped into receipt/payment columns instead of
 * debit/credit. Since {@code AccountingService.resolveCashOrBank} posts
 * every non-cash PaymentMode to the single BANK system account, there is
 * currently exactly one bank account; {@code bankBook} still accepts an
 * optional accountId so a manually-added second bank account (via Account
 * Master) can be selected once one exists.
 */
@Service
@RequiredArgsConstructor
public class CashBankBookService {

    private final JournalDetailRepository journalDetailRepository;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public CashBankBookResponse cashBook(LocalDate fromDate, LocalDate toDate) {
        return bookForAccount(accountService.getSystemAccount(SystemAccountCode.CASH).getId(), fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public CashBankBookResponse bankBook(Long accountId, LocalDate fromDate, LocalDate toDate) {
        Long resolvedId = accountId != null ? accountId : accountService.getSystemAccount(SystemAccountCode.BANK).getId();
        return bookForAccount(resolvedId, fromDate, toDate);
    }

    private CashBankBookResponse bookForAccount(Long accountId, LocalDate fromDate, LocalDate toDate) {
        Account account = accountService.findOrThrow(accountId);

        BigDecimal openingSigned = account.getOpeningBalanceType() == LedgerEntryType.DEBIT
                ? account.getOpeningBalance() : account.getOpeningBalance().negate();

        if (fromDate != null) {
            List<Object[]> before = journalDetailRepository.sumDebitCreditBefore(accountId, fromDate);
            if (!before.isEmpty()) {
                BigDecimal priorDebit = (BigDecimal) before.get(0)[0];
                BigDecimal priorCredit = (BigDecimal) before.get(0)[1];
                openingSigned = openingSigned.add(priorDebit).subtract(priorCredit);
            }
        }

        List<JournalDetail> lines = journalDetailRepository.findLedgerLines(accountId, fromDate, toDate);

        BigDecimal running = openingSigned;
        BigDecimal totalReceipts = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;
        List<CashBankBookRow> rows = new ArrayList<>();
        for (JournalDetail line : lines) {
            JournalHeader journal = line.getJournal();
            running = running.add(line.getDebitAmount()).subtract(line.getCreditAmount());
            totalReceipts = totalReceipts.add(line.getDebitAmount());
            totalPayments = totalPayments.add(line.getCreditAmount());
            rows.add(CashBankBookRow.builder()
                    .voucherDate(journal.getJournalDate())
                    .voucherType(journal.getVoucherType())
                    .voucherNumber(journal.getVoucherNumber())
                    .particulars(line.getNarration() != null ? line.getNarration() : journal.getNarration())
                    .receipt(line.getDebitAmount().signum() > 0 ? line.getDebitAmount() : null)
                    .payment(line.getCreditAmount().signum() > 0 ? line.getCreditAmount() : null)
                    .runningBalance(running)
                    .build());
        }

        return CashBankBookResponse.builder()
                .accountId(account.getId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .fromDate(fromDate)
                .toDate(toDate)
                .openingBalance(openingSigned)
                .rows(rows)
                .totalReceipts(totalReceipts)
                .totalPayments(totalPayments)
                .closingBalance(running)
                .build();
    }
}
