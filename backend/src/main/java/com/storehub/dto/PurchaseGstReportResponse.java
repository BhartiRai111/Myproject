package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class PurchaseGstReportResponse {
    private String returnPeriod;
    private LocalDate fromDate;
    private LocalDate toDate;
    private PagedResponse<GstTransactionRow> transactions;
    private GstSummaryTotals totals;
    private BigDecimal eligibleItcTotal;
}
