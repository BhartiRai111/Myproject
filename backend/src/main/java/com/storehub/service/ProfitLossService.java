package com.storehub.service;

import com.storehub.dto.PnlAccountLine;
import com.storehub.dto.ProfitLossResponse;
import com.storehub.entity.Account;
import com.storehub.entity.AccountType;
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
 * Profit & Loss (spec section 17): a period flow statement built ONLY from
 * accounts classified {@link AccountType#INCOME}/{@link AccountType#EXPENSE}
 * in the Chart of Accounts, never from Sale/Purchase tables directly. Which
 * account an amount lands under is decided entirely by that account's own
 * {@code accountType} — this service never special-cases "Purchase" or
 * "Sales" by name.
 *
 * <p><b>Purchase/inventory treatment (spec section 18):</b> {@code SystemAccountCode.PURCHASE}
 * is itself classified EXPENSE and is debited for the taxable amount on every
 * posted Purchase (see {@code AccountingService.postPurchaseJournal}) — so
 * every purchase is expensed immediately in the period it's posted. The
 * {@code SystemAccountCode.INVENTORY} account exists in the Chart of Accounts
 * but nothing in this codebase ever posts to it (confirmed by inspection):
 * there is no perpetual-inventory / COGS-matching treatment implemented.
 * This is a genuine simplification of the existing Phase 1 accounting
 * design, not something invented here — P&L simply reports what the ledger
 * actually contains.
 */
@Service
@RequiredArgsConstructor
public class ProfitLossService {

    private final JournalDetailRepository journalDetailRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public ProfitLossResponse profitAndLoss(LocalDate fromDate, LocalDate toDate) {
        Map<Long, BigDecimal[]> activityByAccount = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumDebitCreditByAccountRange(fromDate, toDate)) {
            activityByAccount.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }

        List<PnlAccountLine> incomeLines = new ArrayList<>();
        List<PnlAccountLine> expenseLines = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Account account : accountRepository.findByActiveTrueOrderByAccountNameAsc()) {
            BigDecimal[] activity = activityByAccount.get(account.getId());
            if (activity == null) {
                continue;
            }
            BigDecimal debit = activity[0];
            BigDecimal credit = activity[1];

            if (account.getAccountType() == AccountType.INCOME) {
                BigDecimal amount = credit.subtract(debit);
                if (amount.signum() != 0) {
                    incomeLines.add(line(account, amount));
                    totalIncome = totalIncome.add(amount);
                }
            } else if (account.getAccountType() == AccountType.EXPENSE) {
                BigDecimal amount = debit.subtract(credit);
                if (amount.signum() != 0) {
                    expenseLines.add(line(account, amount));
                    totalExpense = totalExpense.add(amount);
                }
            }
        }

        incomeLines.sort(Comparator.comparing(PnlAccountLine::getAccountCode));
        expenseLines.sort(Comparator.comparing(PnlAccountLine::getAccountCode));

        return ProfitLossResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .incomeLines(incomeLines)
                .expenseLines(expenseLines)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netProfitOrLoss(totalIncome.subtract(totalExpense))
                .build();
    }

    /** Just the net figure — used by Balance Sheet to fold current/accumulated P&L into Equity without a duplicate journal entry. */
    @Transactional(readOnly = true)
    public BigDecimal netProfit(LocalDate fromDate, LocalDate toDate) {
        ProfitLossResponse pnl = profitAndLoss(fromDate, toDate);
        return pnl.getNetProfitOrLoss();
    }

    private PnlAccountLine line(Account account, BigDecimal amount) {
        return PnlAccountLine.builder()
                .accountId(account.getId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(amount)
                .build();
    }
}
