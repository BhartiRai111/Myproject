package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Backend-aggregated Expense Summary (Category-wise / Payment-method-wise / Party-wise / GST-wise) for a date
 * range — every grouping is a GROUP BY query in {@code ExpenseRepository}; no per-expense row is ever loaded
 * into this response, so the report stays cheap regardless of history size.
 */
@Getter
@Builder
@AllArgsConstructor
public class ExpenseSummaryReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal totalAmount;
    private long totalCount;
    private List<ExpenseSummaryGroupRow> byCategory;
    private List<ExpenseSummaryGroupRow> byPaymentMode;
    private List<ExpenseSummaryGroupRow> byParty;
    private BigDecimal gstApplicableAmount;
    private BigDecimal nonGstAmount;
    private BigDecimal itcEligibleTax;
    private BigDecimal itcIneligibleTax;
}
