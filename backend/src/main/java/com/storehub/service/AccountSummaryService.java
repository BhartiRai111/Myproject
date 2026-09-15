package com.storehub.service;

import com.storehub.dto.AccountSummaryResponse;
import com.storehub.dto.AccountSummaryRow;
import com.storehub.dto.ExpenseIncomeAccountGroup;
import com.storehub.dto.ExpenseIncomeSummaryResponse;
import com.storehub.dto.ExpenseIncomeVoucherRow;
import com.storehub.entity.Account;
import com.storehub.entity.AccountType;
import com.storehub.entity.LedgerEntryType;
import com.storehub.entity.VoucherType;
import com.storehub.repository.AccountRepository;
import com.storehub.repository.JournalDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account Summary (spec section 23) and Expense/Income Summary (sections
 * 21-22) — all three read the same accounting journal, filtered by
 * AccountType, at either account level (summary) or voucher level (the
 * Expense/Income Summary drill rows).
 */
@Service
@RequiredArgsConstructor
public class AccountSummaryService {

    private final JournalDetailRepository journalDetailRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountSummaryResponse accountSummary(AccountType accountType, LocalDate fromDate, LocalDate toDate) {
        Map<Long, BigDecimal[]> rangeActivity = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumDebitCreditByAccountRange(fromDate, toDate)) {
            rangeActivity.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }

        List<AccountSummaryRow> rows = new ArrayList<>();
        BigDecimal totalOpening = BigDecimal.ZERO;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalClosing = BigDecimal.ZERO;

        for (Account account : accountRepository.findByActiveTrueOrderByAccountNameAsc()) {
            if (accountType != null && account.getAccountType() != accountType) {
                continue;
            }

            BigDecimal openingSigned = account.getOpeningBalanceType() == LedgerEntryType.DEBIT
                    ? account.getOpeningBalance() : account.getOpeningBalance().negate();
            if (fromDate != null) {
                List<Object[]> before = journalDetailRepository.sumDebitCreditBefore(account.getId(), fromDate);
                if (!before.isEmpty()) {
                    openingSigned = openingSigned.add((BigDecimal) before.get(0)[0]).subtract((BigDecimal) before.get(0)[1]);
                }
            }

            BigDecimal[] activity = rangeActivity.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal debit = activity[0];
            BigDecimal credit = activity[1];
            BigDecimal closingSigned = openingSigned.add(debit).subtract(credit);

            if (openingSigned.signum() == 0 && debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }

            rows.add(AccountSummaryRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .accountType(account.getAccountType())
                    .openingBalance(openingSigned)
                    .debit(debit)
                    .credit(credit)
                    .closingBalance(closingSigned)
                    .build());

            totalOpening = totalOpening.add(openingSigned);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            totalClosing = totalClosing.add(closingSigned);
        }

        rows.sort(Comparator.comparing(AccountSummaryRow::getAccountCode));

        return AccountSummaryResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .rows(rows)
                .totalOpening(totalOpening)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .totalClosing(totalClosing)
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseIncomeSummaryResponse expenseSummary(LocalDate fromDate, LocalDate toDate) {
        return voucherGroupedSummary(AccountType.EXPENSE, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public ExpenseIncomeSummaryResponse incomeSummary(LocalDate fromDate, LocalDate toDate) {
        return voucherGroupedSummary(AccountType.INCOME, fromDate, toDate);
    }

    private ExpenseIncomeSummaryResponse voucherGroupedSummary(AccountType accountType, LocalDate fromDate, LocalDate toDate) {
        boolean debitIsAmount = accountType == AccountType.EXPENSE;

        List<ExpenseIncomeAccountGroup> groups = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Account account : accountRepository.findByActiveTrueOrderByAccountNameAsc()) {
            if (account.getAccountType() != accountType) {
                continue;
            }

            List<Object[]> voucherRows = journalDetailRepository.accountVoucherRows(account.getId(), fromDate, toDate);
            if (voucherRows.isEmpty()) {
                continue;
            }

            List<ExpenseIncomeVoucherRow> vouchers = new ArrayList<>();
            BigDecimal accountTotal = BigDecimal.ZERO;
            for (Object[] r : voucherRows) {
                BigDecimal debit = (BigDecimal) r[7];
                BigDecimal credit = (BigDecimal) r[8];
                BigDecimal amount = debitIsAmount ? debit.subtract(credit) : credit.subtract(debit);
                if (amount.signum() == 0) {
                    continue;
                }
                vouchers.add(ExpenseIncomeVoucherRow.builder()
                        .voucherDate((LocalDate) r[0])
                        .journalId((Long) r[1])
                        .journalNumber((String) r[2])
                        .voucherType((VoucherType) r[3])
                        .voucherNumber((String) r[4])
                        .narration(r[5] != null ? (String) r[5] : (String) r[6])
                        .amount(amount)
                        .build());
                accountTotal = accountTotal.add(amount);
            }

            if (vouchers.isEmpty()) {
                continue;
            }

            groups.add(ExpenseIncomeAccountGroup.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .total(accountTotal)
                    .vouchers(vouchers)
                    .build());
            grandTotal = grandTotal.add(accountTotal);
        }

        groups.sort(Comparator.comparing(ExpenseIncomeAccountGroup::getAccountCode));

        return ExpenseIncomeSummaryResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .accounts(groups)
                .totalAmount(grandTotal)
                .build();
    }
}
