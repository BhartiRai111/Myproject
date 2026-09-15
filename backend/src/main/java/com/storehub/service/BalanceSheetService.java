package com.storehub.service;

import com.storehub.dto.BalanceSheetLine;
import com.storehub.dto.BalanceSheetResponse;
import com.storehub.entity.Account;
import com.storehub.entity.AccountType;
import com.storehub.entity.LedgerEntryType;
import com.storehub.repository.AccountRepository;
import com.storehub.repository.JournalDetailRepository;
import com.storehub.util.FinancialYearUtil;
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
 * Balance Sheet (spec section 19), reusing exactly the same per-account
 * "opening balance + all activity up to a date" math as
 * {@code AccountingReportService.trialBalance} — filtered by AccountType
 * instead of listed flat — so Assets/Liabilities here are guaranteed
 * consistent with the Trial Balance. Equity = cumulative (Income - Expense)
 * computed the same way (see class Javadoc on {@link com.storehub.dto.BalanceSheetResponse}
 * for why this is a report-level synthetic line, not a real account).
 */
@Service
@RequiredArgsConstructor
public class BalanceSheetService {

    private final JournalDetailRepository journalDetailRepository;
    private final AccountRepository accountRepository;
    private final ProfitLossService profitLossService;

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(LocalDate asOfDate) {
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();

        Map<Long, BigDecimal[]> activityByAccount = new HashMap<>();
        for (Object[] r : journalDetailRepository.sumDebitCreditByAccount(effectiveAsOf)) {
            activityByAccount.put((Long) r[0], new BigDecimal[]{(BigDecimal) r[1], (BigDecimal) r[2]});
        }

        List<BalanceSheetLine> assetLines = new ArrayList<>();
        List<BalanceSheetLine> liabilityLines = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal cumulativeIncome = BigDecimal.ZERO;
        BigDecimal cumulativeExpense = BigDecimal.ZERO;

        for (Account account : accountRepository.findByActiveTrueOrderByAccountNameAsc()) {
            BigDecimal openingSigned = account.getOpeningBalanceType() == LedgerEntryType.DEBIT
                    ? account.getOpeningBalance() : account.getOpeningBalance().negate();
            BigDecimal[] activity = activityByAccount.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal netSigned = openingSigned.add(activity[0]).subtract(activity[1]);

            switch (account.getAccountType()) {
                case ASSET -> {
                    if (netSigned.signum() != 0) {
                        assetLines.add(line(account, netSigned));
                        totalAssets = totalAssets.add(netSigned);
                    }
                }
                case LIABILITY -> {
                    BigDecimal amount = netSigned.negate();
                    if (amount.signum() != 0) {
                        liabilityLines.add(line(account, amount));
                        totalLiabilities = totalLiabilities.add(amount);
                    }
                }
                case INCOME -> cumulativeIncome = cumulativeIncome.add(netSigned.negate());
                case EXPENSE -> cumulativeExpense = cumulativeExpense.add(netSigned);
            }
        }

        assetLines.sort(Comparator.comparing(BalanceSheetLine::getAccountCode));
        liabilityLines.sort(Comparator.comparing(BalanceSheetLine::getAccountCode));

        BigDecimal retainedEarnings = cumulativeIncome.subtract(cumulativeExpense);
        List<BalanceSheetLine> equityLines = List.of(BalanceSheetLine.builder()
                .accountId(null)
                .accountCode(null)
                .accountName("Retained Earnings (Accumulated Profit/Loss)")
                .amount(retainedEarnings)
                .build());
        BigDecimal totalEquity = retainedEarnings;

        BigDecimal currentYearProfit = profitLossService.netProfit(FinancialYearUtil.startOf(effectiveAsOf), effectiveAsOf);

        BigDecimal difference = totalAssets.subtract(totalLiabilities.add(totalEquity));

        return BalanceSheetResponse.builder()
                .asOfDate(effectiveAsOf)
                .assetLines(assetLines)
                .liabilityLines(liabilityLines)
                .equityLines(equityLines)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .totalEquity(totalEquity)
                .currentYearProfit(currentYearProfit)
                .currentFinancialYear(FinancialYearUtil.label(effectiveAsOf))
                .difference(difference)
                .balanced(difference.compareTo(BigDecimal.ZERO) == 0)
                .build();
    }

    private BalanceSheetLine line(Account account, BigDecimal amount) {
        return BalanceSheetLine.builder()
                .accountId(account.getId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(amount)
                .build();
    }
}
