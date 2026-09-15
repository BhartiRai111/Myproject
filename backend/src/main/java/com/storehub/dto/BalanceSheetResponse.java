package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Balance Sheet (spec section 19). Equity has no dedicated AccountType in
 * this app (Chart of Accounts only classifies ASSET/LIABILITY/INCOME/EXPENSE —
 * confirmed no EQUITY type exists) — {@code equityLines} is therefore always
 * a single synthetic "Retained Earnings (Accumulated Profit/Loss)" line,
 * computed report-side from cumulative Income/Expense activity, never a
 * real Account row, and never a posted journal entry. {@code currentYearProfit}
 * is a separate, informational-only figure (the financial-year-to-date slice
 * of the same P&L) — the balancing Equity total itself is the ALL-TIME
 * accumulated figure, since Assets/Liabilities are themselves all-time
 * cumulative balances.
 */
@Getter
@Builder
@AllArgsConstructor
public class BalanceSheetResponse {
    private LocalDate asOfDate;
    private List<BalanceSheetLine> assetLines;
    private List<BalanceSheetLine> liabilityLines;
    private List<BalanceSheetLine> equityLines;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal currentYearProfit;
    private String currentFinancialYear;
    private BigDecimal difference;
    private boolean balanced;
}
