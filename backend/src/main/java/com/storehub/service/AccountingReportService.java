package com.storehub.service;

import com.storehub.dto.AccountLedgerResponse;
import com.storehub.dto.AccountLedgerRow;
import com.storehub.dto.AccountingDashboardResponse;
import com.storehub.dto.DayBookResponse;
import com.storehub.dto.DayBookRow;
import com.storehub.dto.TrialBalanceResponse;
import com.storehub.dto.TrialBalanceRow;
import com.storehub.entity.Account;
import com.storehub.entity.JournalDetail;
import com.storehub.entity.JournalHeader;
import com.storehub.entity.LedgerEntryType;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.VoucherType;
import com.storehub.repository.AccountRepository;
import com.storehub.repository.JournalDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reports over the posted accounting journal, used to verify the
 * double-entry engine (Section 21 of the spec). These never maintain their
 * own totals; every figure is derived live from JournalHeader/JournalDetail.
 */
@Service
@RequiredArgsConstructor
public class AccountingReportService {

    private final JournalDetailRepository journalDetailRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public DayBookResponse dayBook(LocalDate fromDate, LocalDate toDate) {
        List<Object[]> rawRows = journalDetailRepository.dayBookRows(fromDate, toDate);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<DayBookRow> rows = new java.util.ArrayList<>();

        for (Object[] r : rawRows) {
            BigDecimal debit = (BigDecimal) r[6];
            BigDecimal credit = (BigDecimal) r[7];
            rows.add(DayBookRow.builder()
                    .journalDate((LocalDate) r[0])
                    .journalId((Long) r[1])
                    .journalNumber((String) r[2])
                    .voucherType((VoucherType) r[3])
                    .voucherNumber((String) r[4])
                    .narration((String) r[5])
                    .debit(debit)
                    .credit(credit)
                    .build());
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        return DayBookResponse.builder()
                .rows(rows)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .build();
    }

    @Transactional(readOnly = true)
    public AccountLedgerResponse accountLedger(Long accountId, LocalDate fromDate, LocalDate toDate) {
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
        List<AccountLedgerRow> rows = new java.util.ArrayList<>();
        for (JournalDetail line : lines) {
            JournalHeader journal = line.getJournal();
            running = running.add(line.getDebitAmount()).subtract(line.getCreditAmount());
            rows.add(AccountLedgerRow.builder()
                    .journalDate(journal.getJournalDate())
                    .voucherType(journal.getVoucherType())
                    .voucherNumber(journal.getVoucherNumber())
                    .particulars(line.getNarration() != null ? line.getNarration() : journal.getNarration())
                    .debit(line.getDebitAmount())
                    .credit(line.getCreditAmount())
                    .balance(running.abs())
                    .balanceType(running.signum() >= 0 ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT)
                    .build());
        }

        return AccountLedgerResponse.builder()
                .accountId(account.getId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .openingBalance(openingSigned.abs())
                .openingBalanceType(openingSigned.signum() >= 0 ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT)
                .rows(rows)
                .closingBalance(running.abs())
                .closingBalanceType(running.signum() >= 0 ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT)
                .build();
    }

    @Transactional(readOnly = true)
    public AccountingDashboardResponse getDashboardSummary() {
        return AccountingDashboardResponse.builder()
                .cashBalance(currentBalance(SystemAccountCode.CASH))
                .bankBalance(currentBalance(SystemAccountCode.BANK))
                .receivableBalance(currentBalance(SystemAccountCode.CUSTOMER_RECEIVABLE))
                .payableBalance(currentBalance(SystemAccountCode.SUPPLIER_PAYABLE).negate())
                .build();
    }

    /** Current signed balance (debit-positive) of a system account: opening balance plus all posted/reversed activity. */
    private BigDecimal currentBalance(SystemAccountCode code) {
        Account account = accountService.getSystemAccount(code);
        BigDecimal openingSigned = account.getOpeningBalanceType() == LedgerEntryType.DEBIT
                ? account.getOpeningBalance() : account.getOpeningBalance().negate();
        List<Object[]> activity = journalDetailRepository.sumDebitCreditForAccount(account.getId());
        if (activity.isEmpty()) {
            return openingSigned;
        }
        BigDecimal debit = (BigDecimal) activity.get(0)[0];
        BigDecimal credit = (BigDecimal) activity.get(0)[1];
        return openingSigned.add(debit).subtract(credit);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(LocalDate asOfDate) {
        Map<Long, BigDecimal[]> activityByAccount = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumDebitCreditByAccount(asOfDate)) {
            activityByAccount.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }

        List<Account> accounts = accountRepository.findByActiveTrueOrderByAccountNameAsc().stream()
                .sorted(Comparator.comparing(Account::getAccountCode))
                .toList();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<TrialBalanceRow> rows = new java.util.ArrayList<>();

        for (Account account : accounts) {
            BigDecimal openingSigned = account.getOpeningBalanceType() == LedgerEntryType.DEBIT
                    ? account.getOpeningBalance() : account.getOpeningBalance().negate();
            BigDecimal[] activity = activityByAccount.getOrDefault(account.getId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal netSigned = openingSigned.add(activity[0]).subtract(activity[1]);

            BigDecimal debit = netSigned.signum() > 0 ? netSigned : BigDecimal.ZERO;
            BigDecimal credit = netSigned.signum() < 0 ? netSigned.negate() : BigDecimal.ZERO;

            rows.add(TrialBalanceRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .accountType(account.getAccountType())
                    .debit(debit)
                    .credit(credit)
                    .build());
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        return TrialBalanceResponse.builder()
                .asOfDate(asOfDate)
                .rows(rows)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .balanced(totalDebit.compareTo(totalCredit) == 0)
                .build();
    }
}
