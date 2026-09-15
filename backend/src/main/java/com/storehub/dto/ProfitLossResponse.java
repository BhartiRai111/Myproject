package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Profit & Loss (spec section 17), derived ONLY from accounts classified
 * AccountType.INCOME/EXPENSE in the Chart of Accounts — never computed from
 * Sale/Purchase tables directly. See {@code ProfitLossService} for the
 * Purchase-as-expense treatment this app's accounting model actually uses.
 */
@Getter
@Builder
@AllArgsConstructor
public class ProfitLossResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<PnlAccountLine> incomeLines;
    private List<PnlAccountLine> expenseLines;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfitOrLoss;
}
